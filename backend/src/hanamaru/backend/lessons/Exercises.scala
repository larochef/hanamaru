package hanamaru.backend.lessons

import hanamaru.backend.kanji.KanjiSight
import hanamaru.backend.learner.*
import hanamaru.backend.reference.*
import hanamaru.shared.characters.CharacterAsked
import hanamaru.shared.kana.Romaji
import hanamaru.shared.kanji.KanjiExercise
import hanamaru.shared.learner.Grade
import hanamaru.shared.lessons.*
import hanamaru.shared.text.{KanjiShown, SentencePart}

import java.time.Instant
import scala.util.Random

/** The exercises of the curriculum's lessons, made in the sentences of a
  * point's examples and of a new reading text ([[Lessons]]): choices (a
  * particle, a form), translations both ways, kanji written; and, once done,
  * how they went, recorded as one review of the point.
  */
final class Exercises(
    lessons: Lessons,
    analyser: () => Option[Analyser],
    dictionary: () => Option[Dictionary],
    learner: LearnerStore,
    kanjiBook: () => Option[KanjiBook]
) {
  private val points = Curriculum.points

  /** Exercises on a lesson's point, in the sentences of its examples and of a
    * new reading text that show it: its particle chosen, when the point is a
    * particle, then sentences translated from French, a few of each, shuffled.
    */
  def exercises(id: String): Either[String, List[LessonExercise]] =
    for {
      index <- lessons.indexOf(id)
      analysis <- analyser().toRight("The Japanese analyser is not ready")
      words <- dictionary().toRight("The dictionary is not ready")
    } yield {
      val point = points(index)
      val glosses = Glosses(words)
      val sight = KanjiSight.of(learner)
      val ofExamples = point.examples.map(example =>
        (example.japanese, example.french, analysis.tokens(example.japanese))
      )
      val ofText = Option
        .when(point.text)(lessons.written(index, analysis, words))
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
            sight.shown(lessons.shown(point, tokens, french, glosses).parts),
            japanese,
            french,
            Random.shuffle(expected :: distractors(index, point, expected)),
            expected
          )
        }
      }
      val forms = sentences.flatMap { (japanese, french, tokens) =>
        val at = point.shownIn(tokens).toList.sorted
        val ending = at.map(tokens(_).surface).mkString
        Option.when(
          at.nonEmpty && at == (at.head to at.last).toList && at.head > 0 &&
            Exercises.endings.exists(_._1 == ending) &&
            formDistractors(index, ending).nonEmpty &&
            tokens(at.head - 1).partOfSpeech.headOption.contains("動詞")
        ) {
          LessonExercise(
            point.id,
            ExerciseKind.Form,
            Exercises.markedTogether(
              sight.shown(lessons.shown(point, tokens, french, glosses).parts)
            ),
            japanese,
            french,
            Random.shuffle(ending :: formDistractors(index, ending)),
            ending
          )
        }
      }
      val translations = sentences.map { (japanese, french, tokens) =>
        val parts =
          sight.shown(lessons.shown(point, tokens, french, glosses).parts)
        LessonExercise(
          point.id,
          ExerciseKind.Translation,
          parts,
          japanese,
          french,
          Nil,
          parts.map(_.text).mkString,
          // the script expected said: hiragana, and the words in katakana
          wordsInKatakana = parts.count(part => Romaji.isKatakana(part.text))
        )
      }
      val choices = (particles ++ forms).take(exercisesOfAKind)
      val translated = translations.take(translationsAtMost)
      // the meanings asked in sentences not shown with their French yet in
      // the series (chosen or translated), which would give them away
      val shownInFrench = (choices ++ translated).map(_.japanese).toSet
      val meanings = sentences
        .filterNot((japanese, _, _) => shownInFrench(japanese))
        .take(meaningsAtMost)
        .map { (japanese, french, tokens) =>
          LessonExercise(
            point.id,
            ExerciseKind.Meaning,
            sight.shown(lessons.shown(point, tokens, french, glosses).parts),
            japanese,
            french,
            Nil,
            french
          )
        }
      val kanjiToWrite = kanjiWritings(sentences, sight, words, glosses, point)
      choices ++ translated ++ meanings ++ kanjiToWrite
    }

  private val translationsAtMost = 3
  private val meaningsAtMost = 2
  private val kanjiWritingsAtMost = 2

  // A few words of the sentences whose kanji's writing is due: one kanji of
  // each written in its word, a review of the kanji.
  private def kanjiWritings(
      sentences: List[(String, String, List[Token])],
      sight: KanjiSight,
      words: Dictionary,
      glosses: Glosses,
      point: CurriculumPoint
  ): List[LessonExercise] =
    kanjiBook().toList.flatMap { book =>
      // only those due: a review out of time would reschedule them early
      val now = Instant.now().toEpochMilli
      val writing = learner
        .cards(ItemKind.Kanji)
        .filter(card => card.kind == CardKind.Writing && card.due <= now)
        .map(_.key)
        .toSet
      sentences
        .flatMap { (japanese, french, tokens) =>
          val parts =
            sight.shown(lessons.shown(point, tokens, french, glosses).parts)
          parts.flatMap(part =>
            for {
              written <- part.written
              literal <- written
                .map(_.toString)
                .find(character => writing(character))
              shownWord <- part.word
              word <- Units.chosen(
                words.withForm(shownWord.form),
                shownWord.form,
                None
              )
              kanji <- book.kanji(literal)
            } yield LessonExercise(
              point.id,
              ExerciseKind.KanjiWriting,
              // the word to write in kana, marked: in kanji, it would give
              // its kanji away
              parts.map(each =>
                if (each eq part)
                  each.copy(marked = true, shownAs = KanjiShown.Kana)
                else each.copy(marked = false)
              ),
              japanese,
              french,
              Nil,
              literal,
              Some(KanjiExercise(kanji, word, CharacterAsked.Writing, false))
            )
          )
        }
        .distinctBy(_.expected)
        .take(kanjiWritingsAtMost)
    }

  // other endings for a form, among those learned by this point: none until
  // there are enough to choose from (ます alone, nothing to tell apart)
  private def formDistractors(index: Int, expected: String): List[String] = {
    val learned = points.take(index + 1).map(_.id).toSet
    val known = Exercises.endings.collect {
      case (ending, point) if learned(point) => ending
    }
    if (known.size < choicesOfAForm) Nil
    else Random.shuffle(known.filter(_ != expected)).take(choicesOfAForm - 1)
  }

  private val choicesOfAForm = 4

  /** A lesson's exercises done, recorded as one review of its point: Good with
    * four answers in five right, Hard with half, else Again. A kanji written is
    * the kanji's review, not counted here. How it went, as counted.
    */
  def done(result: ExercisesDone, at: Instant): Either[String, ExercisesTally] =
    lessons.indexOf(result.point).map { _ =>
      val counted =
        result.outcomes.filter(_.kind != ExerciseKind.KanjiWriting)
      val tally = ExercisesTally(counted.count(_.right), counted.size)
      val share =
        if (tally.total == 0) 0.0 else tally.right.toDouble / tally.total
      val grade =
        if (share >= 0.8) Grade.Good
        else if (share >= 0.5) Grade.Hard
        else Grade.Again
      learner.reviewed(
        ItemKind.Grammar,
        result.point,
        CardKind.Usage,
        grade,
        s"${tally.right}/${tally.total}",
        at
      )
      tally
    }

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

}

object Exercises {

  /** The endings a verb's polite stem takes (たべ＋ます), with the point teaching
    * each: one blanked is chosen among the others, the French saying which
    * (tense, negation, wish, invitation).
    */
  val endings: List[(String, String)] = List(
    "ます" -> "masu",
    "ません" -> "masen-mashita",
    "ました" -> "masen-mashita",
    "ませんでした" -> "masen-mashita",
    "ましょう" -> "mashou",
    "たい" -> "tai"
  )

  /** The marked parts next to each other made one, in kana, to be blanked as
    * one: ませ, ん, でし, た as ませんでした.
    */
  def markedTogether(parts: List[SentencePart]): List[SentencePart] =
    parts
      .foldLeft(Vector.empty[SentencePart])((done, part) =>
        done.lastOption match {
          case Some(last) if last.marked && part.marked && !part.spaceBefore =>
            done.init :+ last.copy(
              text = last.text + part.text,
              written = None,
              shownAs = KanjiShown.Kana
            )
          case _ => done :+ part
        }
      )
      .toList

}
