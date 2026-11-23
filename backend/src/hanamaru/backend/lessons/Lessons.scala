package hanamaru.backend.lessons

import hanamaru.backend.learner.*
import hanamaru.backend.reference.*
import hanamaru.shared.learner.Grade
import hanamaru.shared.lessons.*

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import scala.util.Random

/** The curriculum's lessons: their examples laid out in kana by the analyser,
  * the grammar each shows marked and each word glossed by the dictionary; and
  * their reading texts, written by the tutor.
  */
final class Lessons(
    analyser: () => Option[Analyser],
    dictionary: () => Option[Dictionary],
    learner: LearnerStore,
    writer: TextWriter
) {
  private val points = Curriculum.points
  private val laidOut = ConcurrentHashMap[String, GrammarLesson]()

  def curriculum: List[GrammarPoint] = points.map(_.summary)

  def lesson(id: String): Either[String, GrammarLesson] =
    Option(laidOut.get(id)).map(Right(_)).getOrElse {
      for {
        index <- indexOf(id)
        analysis <- analyser().toRight("The Japanese analyser is not ready")
        words <- dictionary().toRight("The dictionary is not ready")
      } yield {
        val point = points(index)
        val lesson = GrammarLesson(
          point.summary,
          point.requires
            .flatMap(required => points.find(_.id == required))
            .map(_.summary),
          point.explanation,
          point.examples.map(example =>
            shown(
              point,
              analysis.tokens(example.japanese),
              example.french,
              Glosses(words)
            )
          ),
          points.lift(index - 1).map(_.summary),
          points.lift(index + 1).map(_.summary),
          point.text
        )
        laidOut.put(id, lesson)
        lesson
      }
    }

  /** A new reading text for a lesson; none when the tutor could not write one
    * the code accepts.
    */
  def text(id: String): Either[String, Option[LessonText]] =
    for {
      index <- indexOf(id).filterOrElse(
        points(_).text,
        s"No reading text for $id"
      )
      analysis <- analyser().toRight("The Japanese analyser is not ready")
      words <- dictionary().toRight("The dictionary is not ready")
    } yield written(index, analysis, words).map(sentences =>
      LessonText(
        sentences.map((sentence, tokens) =>
          shown(points(index), tokens, sentence.french, Glosses(words))
        )
      )
    )

  /** Exercises on a lesson's point, in the sentences of its examples and of a
    * new reading text that show it: its particle chosen, when the point is a
    * particle, then sentences translated from French, a few of each, shuffled.
    */
  def exercises(id: String): Either[String, List[LessonExercise]] =
    for {
      index <- indexOf(id)
      analysis <- analyser().toRight("The Japanese analyser is not ready")
      words <- dictionary().toRight("The dictionary is not ready")
    } yield {
      val point = points(index)
      val glosses = Glosses(words)
      val ofExamples = point.examples.map(example =>
        (example.japanese, example.french, analysis.tokens(example.japanese))
      )
      val ofText = Option
        .when(point.text)(written(index, analysis, words))
        .flatten
        .toList
        .flatten
        .map((sentence, tokens) => (sentence.japanese, sentence.french, tokens))
        .filter((_, _, tokens) => point.shownIn(tokens).nonEmpty)
      // a sentence of the text may be an example again: asked once
      val sentences = Random.shuffle(
        (ofExamples ++ ofText).distinctBy((_, _, tokens) =>
          tokens.map(_.surface).mkString
        )
      )
      val particles = sentences.flatMap { (japanese, french, tokens) =>
        val shownAt = point.shownIn(tokens)
        val marked = shownAt.toList.map(tokens(_))
        Option.when(
          marked.nonEmpty && marked.forall(
            _.partOfSpeech.headOption.contains("助詞")
          ) &&
            marked.map(_.surface).distinct.size == 1
        ) {
          val expected = marked.head.surface
          LessonExercise(
            point.id,
            ExerciseKind.Particle,
            shown(point, tokens, french, glosses).parts,
            japanese,
            french,
            Random.shuffle(expected :: distractors(index, point, expected)),
            expected
          )
        }
      }
      val translations = sentences.map { (japanese, french, tokens) =>
        val parts = shown(point, tokens, french, glosses).parts
        LessonExercise(
          point.id,
          ExerciseKind.Translation,
          parts,
          japanese,
          french,
          Nil,
          parts.map(_.text).mkString
        )
      }
      particles.take(exercisesOfAKind) ++ translations.take(
        exercisesOfAKind + (exercisesOfAKind - particles.size).max(0) / 2
      )
    }

  /** A lesson's exercises done, recorded as one review of its point: Good with
    * four answers in five right, Hard with half, else Again.
    */
  def done(result: ExercisesDone, at: Instant): Either[String, Unit] =
    indexOf(result.point).map { _ =>
      val share =
        if (result.total == 0) 0.0 else result.right.toDouble / result.total
      val grade =
        if (share >= 0.8) Grade.Good
        else if (share >= 0.5) Grade.Hard
        else Grade.Again
      learner.reviewed(
        ItemKind.Grammar,
        result.point,
        CardKind.Usage,
        grade,
        s"${result.right}/${result.total}",
        at
      )
    }

  /** A lesson session's plan: the points due for review, two at most, in the
    * curriculum's order, then the first point never practised nor placed.
    */
  def plan(now: Instant): LessonPlan = {
    val due =
      learner.dueKeys(ItemKind.Grammar, now.toEpochMilli, reviewsAtMost).toSet
    val met = learner.metKeys(ItemKind.Grammar)
    LessonPlan(
      points.filter(point => due(point.id)).map(_.summary),
      points.find(point => !met(point.id)).map(_.summary)
    )
  }

  private val reviewsAtMost = 2

  private val exercisesOfAKind = 4

  // three other particles: those of the points learned first, then those to
  // come; never another form of the same point (へ for に)
  private def distractors(
      index: Int,
      point: CurriculumPoint,
      expected: String
  ): List[String] = {
    def particlesOf(some: List[CurriculumPoint]) = some
      .filterNot(_.id == point.id)
      .flatMap(_.signature.flatten)
      .filter(_.pos.exists(_.startsWith("助詞")))
      .flatMap(_.form)
    val own = point.signature.flatten.flatMap(_.form).toSet + expected
    (Random.shuffle(particlesOf(points.take(index + 1)).distinct) ++
      particlesOf(points.drop(index + 1)).distinct)
      .filterNot(own)
      .distinct
      .take(3)
  }

  // a new reading text: the words the learner has met, and those of the
  // lessons' examples so far, are the words allowed
  private def written(index: Int, analysis: Analyser, words: Dictionary) = {
    val met = words.words(
      learner.metKeys(ItemKind.Word).flatMap(_.toLongOption).toList
    )
    val ofLessons = points
      .take(index + 1)
      .flatMap(_.examples)
      .flatMap(example => analysis.tokens(example.japanese))
      .filterNot(_.isGrammar)
      .map(_.dictionaryForm)
      .++(points.take(index + 1).flatMap(_.words))
      .distinct
    val known = ofLessons ++ met.flatMap(word =>
      word.writings.headOption.orElse(word.readings.headOption)
    )
    // the lessons' words written in kana too: いく as well as 行く
    val knownForms = (ofLessons.flatMap(words.withForm) ++ met)
      .flatMap(word => word.writings ++ word.readings)
      .toSet ++ ofLessons
    writer.written(index, analysis, known.distinct, knownForms, points)
  }

  private def indexOf(id: String): Either[String, Int] =
    Right(points.indexWhere(_.id == id)).filterOrElse(_ >= 0, s"No lesson $id")

  // a sentence in kana, the point's grammar marked, its words glossed
  private def shown(
      point: CurriculumPoint,
      tokens: List[Token],
      french: String,
      glosses: Glosses
  ): LessonExample =
    LessonExample(
      SentenceLayout.laidOut(tokens, point.shownIn(tokens), word = glosses.of),
      french
    )
}
