package hanamaru.backend.lessons

import hanamaru.backend.kanji.KanjiSight
import hanamaru.backend.learner.*
import hanamaru.backend.reference.*
import hanamaru.shared.characters.CharactersOf
import hanamaru.shared.lessons.*
import hanamaru.shared.vocabulary.SeriesOf

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** The curriculum's lessons: their examples laid out in kana by the analyser,
  * the grammar each shows marked and each word glossed by the dictionary; and
  * their reading texts, written by the tutor.
  */
final class Lessons(
    analyser: () => Option[Analyser],
    dictionary: () => Option[Dictionary],
    learner: LearnerStore,
    writer: TextWriter,
    units: Units
) {
  private val points = Curriculum.points
  private val laidOut = ConcurrentHashMap[String, GrammarLesson]()

  def curriculum: List[GrammarPoint] = points.map(_.summary)

  /** A point's lesson, its examples' kanji shown as the learner reads them. */
  def lesson(id: String): Either[String, GrammarLesson] = {
    val sight = KanjiSight.of(learner)
    laidOutLesson(id).map(lesson =>
      lesson.copy(examples = lesson.examples.map(seen(_, sight)))
    )
  }

  // an example's words with kanji shown as the learner reads them
  private def seen(example: LessonExample, sight: KanjiSight) =
    example.copy(parts = sight.shown(example.parts))

  private def laidOutLesson(id: String): Either[String, GrammarLesson] =
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
          point.text,
          point.theme,
          units.wordsOf(point)
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
    } yield {
      val sight = KanjiSight.of(learner)
      written(index, analysis, words).map(sentences =>
        LessonText(
          sentences.map((sentence, tokens) =>
            seen(
              shown(points(index), tokens, sentence.french, Glosses(words)),
              sight
            )
          )
        )
      )
    }

  /** The unit under way, with how far it is. */
  def unit: Option[UnitProgress] =
    units.current.map { standing =>
      UnitProgress(
        standing.point.summary,
        standing.point.theme,
        standing.words,
        standing.stable,
        standing.wordsNeeded,
        standing.wordsAcquired,
        standing.practised,
        standing.symbols.map(symbols =>
          SymbolsProgress(
            symbols.kind,
            if (symbols.kind == SymbolKind.Kanji) symbols.characters else Nil,
            standing.symbolsLearned,
            symbols.characters.size,
            standing.symbolsLearnedAll
          )
        ),
        if (standing.acquired) UnitWaitsFor.MovingOn
        else if (!standing.symbolsMet) UnitWaitsFor.KanaToMeet
        else if (!standing.wordsAcquired) UnitWaitsFor.Words
        else if (!standing.practised) UnitWaitsFor.Lesson
        else UnitWaitsFor.Symbols,
        Option
          .when(standing.acquired)(units.onward)
          .flatten
          .map((acquired, next) =>
            UnitOnward(acquired.map(_.summary), next.map(_.summary))
          )
      )
    }

  /** Moves on from the unit under way, once it is acquired; the unit under way
    * then.
    */
  def movedOn(at: Instant): Option[UnitProgress] = {
    units.moveOn(at)
    unit
  }

  /** A lesson session's plan: the points due for review, two at most, in the
    * curriculum's order; the series `wordsToPractise`; the lesson of the unit
    * under way, if its point was never practised nor placed (past the last
    * unit, the first point never practised), once its kana are met; its kana
    * and kanji, `charactersToPractise`.
    */
  def plan(
      now: Instant,
      wordsToPractise: Option[SeriesOf],
      charactersToPractise: Option[CharactersOf]
  ): LessonPlan = {
    val due =
      learner.dueKeys(ItemKind.Grammar, now.toEpochMilli, reviewsAtMost).toSet
    val current = units.current
    LessonPlan(
      points.filter(point => due(point.id)).map(_.summary),
      wordsToPractise,
      Option
        .when(current.forall(_.symbolsMet))(
          Lessons.nextLesson(
            points,
            current.map(_.point),
            learner.metKeys(ItemKind.Grammar)
          )
        )
        .flatten
        .map(_.summary),
      charactersToPractise
    )
  }

  private val reviewsAtMost = 2

  /** A new reading text for the lesson at `index`, its sentences with their
    * tokens: the words the learner has met, and those of the lessons' examples
    * so far, are the words allowed.
    */
  def written(index: Int, analysis: Analyser, words: Dictionary) = {
    val met = words.words(
      learner.metKeys(ItemKind.Word).flatMap(_.toLongOption).toList
    )
    val (ofLessons, ofLessonsForms) = lessonWords(index, analysis, words)
    val known = ofLessons ++ met.flatMap(word =>
      word.writings.headOption.orElse(word.readings.headOption)
    )
    val knownForms =
      ofLessonsForms ++ met.flatMap(word => word.writings ++ word.readings)
    writer.written(index, analysis, known.distinct, knownForms, points)
  }

  private val lessonWordsUpTo =
    ConcurrentHashMap[Int, (List[String], Set[String])]()

  // The words of the lessons up to one, theirs and their examples', as written,
  // and every form of them (いく as well as 行く): the same for every text of a
  // lesson, looked up in the dictionary once.
  private def lessonWords(index: Int, analysis: Analyser, words: Dictionary) =
    lessonWordsUpTo.computeIfAbsent(
      index,
      _ => {
        val written = points
          .take(index + 1)
          .flatMap(_.examples)
          .flatMap(example => analysis.tokens(example.japanese))
          .filterNot(_.isGrammar)
          .map(_.dictionaryForm)
          .++(points.take(index + 1).flatMap(_.wordForms))
          .distinct
        val forms = words
          .withForms(written)
          .flatMap(word => word.writings ++ word.readings)
          .toSet ++ written
        (written, forms)
      }
    )

  /** Where a point is in the curriculum. */
  def indexOf(id: String): Either[String, Int] =
    Right(points.indexWhere(_.id == id)).filterOrElse(_ >= 0, s"No lesson $id")

  /** A sentence in kana, the point's grammar marked, its words glossed. */
  def shown(
      point: CurriculumPoint,
      tokens: List[Token],
      french: String,
      glosses: Glosses
  ): LessonExample = {
    val words = tokens.map(token => token -> glosses.of(token)).toMap
    LessonExample(
      SentenceLayout.laidOut(
        tokens,
        point.shownIn(tokens),
        // a word as it stands in the dictionary is read as the dictionary
        // reads it: はこ, not the ばこ the analyser may guess
        reading = token =>
          words
            .get(token)
            .flatten
            .filter(_ => token.surface == token.dictionaryForm)
            .map(_.reading),
        word = words.get(_).flatten
      ),
      french
    )
  }
}

object Lessons {

  /** The lesson to learn next: the unit under way's, unless its point was met
    * already (practised or placed), its words then being what is left of it;
    * past the last unit, the first point never met.
    */
  def nextLesson(
      points: List[CurriculumPoint],
      unit: Option[CurriculumPoint],
      met: Set[String]
  ): Option[CurriculumPoint] = unit match {
    case Some(point) => Option.unless(met(point.id))(point)
    case None        => points.find(point => !met(point.id))
  }
}
