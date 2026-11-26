package hanamaru.backend.vocabulary

import hanamaru.backend.kanji.KanjiSight
import hanamaru.backend.learner.*
import hanamaru.backend.lessons.Units
import hanamaru.backend.reference.Dictionary
import hanamaru.backend.writing.{WritingAttempts, WritingReview}
import hanamaru.shared.learner.Grade
import hanamaru.shared.vocabulary.*

import java.time.{Duration, Instant}
import scala.util.Random

import com.github.plokhotnyuk.jsoniter_scala.core.writeToString

/** Vocabulary practised in rapid series: each answer judged (by code, then by
  * the tutor for a meaning the code does not find, then by the learner when
  * neither can tell) and kept as a review of the word's card for that
  * direction.
  */
final class VocabularyPractice(
    dictionaryReady: () => Option[Dictionary],
    learner: LearnerStore,
    judge: MeaningJudge,
    attempts: WritingAttempts,
    writing: WritingReview,
    sentences: SentenceWriter,
    units: Units
) {
  private val largestSeries = 10
  private val mostNew = 5

  private def dictionary: Either[String, Dictionary] =
    dictionaryReady().toRight("The dictionary is not ready")

  /** The words of a series: those due first, the most overdue first, then new
    * ones, fewer when many are due: the words of the unit under way, then, once
    * they are all met, those of them not stable yet; past the last unit, the
    * core vocabulary in the order it is learned. A unit's words all met, no new
    * word comes until the unit is acquired, its lesson included; a first unit's
    * words wait until its kana are met. A series the learner asked for
    * ([[SeriesOf.Free]]) is filled up with words met ([[Rotation]]), those not
    * reviewed for a day first: it never comes empty, and every word comes back.
    * One unit's ([[SeriesOf.OfUnit]]): [[ofUnit]].
    */
  def series(at: Instant, of: SeriesOf): Either[String, List[SeriesWord]] =
    of match {
      case SeriesOf.MyLesson      => byUnits(at, fill = false)
      case SeriesOf.Free          => byUnits(at, fill = true)
      case SeriesOf.OfUnit(point) => ofUnit(point, at)
    }

  private def byUnits(
      at: Instant,
      fill: Boolean
  ): Either[String, List[SeriesWord]] = dictionary.map { words =>
    val due = learner
      .dueKeys(ItemKind.Word, at.toEpochMilli, largestSeries)
      .flatMap(_.toLongOption)
    val met = learner.metKeys(ItemKind.Word).flatMap(_.toLongOption)
    val newCount = (largestSeries - due.size).min(mostNew).max(0)
    val dueWords = words.words(due)
    val now = units.standing()
    // A word is asked in a sentence once its production has passed its
    // first steps: context too early takes attention from the word itself.
    def stable(word: Long) = now.stable(word)
    // the words reviewed lately, which a rotation takes last
    lazy val lately = learner
      .reviewedSince(ItemKind.Word, at.minus(Duration.ofDays(1)).toEpochMilli)
      .flatMap(_.toLongOption)
    val fresh = units.current match {
      // an acquired unit practised on, the learner not moved on yet: its
      // words in turn
      case Some(standing) if standing.acquired =>
        Rotation
          .drawn(
            standing.words
              .filter(word => met(word.id) && !due.contains(word.id)),
            newCount,
            word => lately(word.id),
            Random
          )
          .map(word =>
            SeriesWord(word, isNew = false, inSentence = stable(word.id))
          )
      // the first unit's words wait for its hiragana: they are written with
      // them
      case Some(standing) if !standing.symbolsMet => Nil
      case Some(standing)                         =>
        val unmet = standing.words.filterNot(word => met(word.id))
        val unsettled = standing.words.filter(word =>
          met(word.id) && !due.contains(word.id) && !stable(word.id)
        )
        unmet.take(newCount).map(SeriesWord(_, isNew = true)) ++
          unsettled
            .take((newCount - unmet.size).max(0))
            .map(SeriesWord(_, isNew = false))
      case None =>
        words.nextWords(met, newCount).map(SeriesWord(_, isNew = true))
    }
    val planned = dueWords.map(word =>
      SeriesWord(word, isNew = false, inSentence = stable(word.id))
    ) ++ fresh
    val room = largestSeries - planned.size
    val filled =
      if (!fill || room <= 0) Nil
      else {
        val taken = planned.map(_.word.id).toSet
        words
          .words(
            Rotation.drawn(
              met.toList.filterNot(taken),
              room,
              lately,
              Random
            )
          )
          .map(word =>
            SeriesWord(word, isNew = false, inSentence = stable(word.id))
          )
      }
    planned ++ filled
  }

  /** A series on one unit's words (the unit one of those so far), asked for by
    * the learner on the unit's page: those due first, then a few never met,
    * then the others met, in turn ([[Rotation]]).
    */
  private def ofUnit(
      point: String,
      at: Instant
  ): Either[String, List[SeriesWord]] = for {
    _ <- dictionary
    unit <- units.soFar.find(_.id == point).toRight(s"No unit $point so far")
  } yield {
    val words = units.wordsOf(unit)
    val now = units.standing()
    val met = learner.metKeys(ItemKind.Word).flatMap(_.toLongOption)
    val dueNow = learner
      .cards(ItemKind.Word)
      .filter(_.due <= at.toEpochMilli)
      .flatMap(_.key.toLongOption)
      .toSet
    val due = words.filter(word => dueNow(word.id)).take(largestSeries)
    // the first unit's words are written in hiragana: none new before they
    // are met
    val fresh =
      if (!units.progressOf(unit, now).symbolsMet) Nil
      else words.filterNot(word => met(word.id)).take(mostNew)
    val lately = learner
      .reviewedSince(ItemKind.Word, at.minus(Duration.ofDays(1)).toEpochMilli)
      .flatMap(_.toLongOption)
    val others = Rotation.drawn(
      words.filter(word => met(word.id) && !dueNow(word.id)),
      largestSeries - due.size - fresh.size,
      word => lately(word.id),
      Random
    )
    due.map(word =>
      SeriesWord(word, isNew = false, inSentence = now.stable(word.id))
    ) ++ fresh.map(SeriesWord(_, isNew = true)) ++ others.map(word =>
      SeriesWord(word, isNew = false, inSentence = now.stable(word.id))
    )
  }

  /** A sentence for a word, made of the words the learner has met, its kanji
    * shown as the learner reads them.
    */
  def sentence(word: Long): Either[String, Option[WordSentence]] =
    dictionary.map { words =>
      val met =
        learner.metKeys(ItemKind.Word).flatMap(_.toLongOption).filter(_ != word)
      words
        .words(List(word))
        .headOption
        .flatMap(asked => sentences.written(asked, words.words(met.toList)))
        .map(written =>
          written.copy(parts = KanjiSight.of(learner).shown(written.parts))
        )
    }

  def answered(
      answer: VocabularyAnswer,
      at: Instant
  ): Either[String, VocabularyVerdict] =
    for {
      words <- dictionary
      word <- words
        .words(List(answer.word))
        .headOption
        .toRight(s"No word ${answer.word}")
    } yield {
      val (verdict, tutorMatched) = answer.direction match {
        case Direction.Production if answer.written.nonEmpty =>
          answer.written.foreach { attempt =>
            attempts.record(attempt)
            writing.writtenInWord(attempt, at)
          }
          AnswerGrading.handwritten(answer.written, word) -> None
        case Direction.Production =>
          AnswerGrading.production(answer.typed, word, words.withForm) -> None
        case Direction.Meaning if answer.typed.trim.isEmpty =>
          Verdict.Wrong -> None
        case Direction.Meaning if AnswerGrading.meaning(answer.typed, word) =>
          Verdict.Right -> None
        case Direction.Meaning =>
          judge.recognised(word, answer.typed) match {
            case Some(gloss) => Verdict.Right -> Some(gloss)
            case None        => Verdict.Unsure -> None
          }
      }
      val grade = verdict match {
        case Verdict.Right if answer.alreadyKnown    => Some(Grade.Easy)
        case Verdict.Right                           => Some(Grade.Good)
        case Verdict.NearMiss | Verdict.OtherWord(_) => Some(Grade.Hard)
        case Verdict.Wrong                           => Some(Grade.Again)
        case Verdict.Unsure                          => None
      }
      grade.foreach(chosen =>
        reviewed(
          answer.word,
          answer.direction,
          chosen,
          writeToString(answer),
          at
        )
      )
      VocabularyVerdict(
        verdict,
        grade,
        tutorMatched,
        VocabularyPractice.comesBack(
          verdict == Verdict.Wrong,
          answer.attempt
        )
      )
    }

  /** The learner's own word on an answer neither the code nor the tutor could
    * judge.
    */
  def selfGraded(grade: SelfGrade, at: Instant): ComesBack = {
    reviewed(
      grade.word,
      grade.direction,
      if (grade.right) Grade.Good else Grade.Again,
      writeToString(grade),
      at
    )
    ComesBack(VocabularyPractice.comesBack(!grade.right, grade.attempt))
  }

  // A new word said to be known and answered right waits for its next review
  // to be confirmed, as the placement test's answers do.
  private def reviewed(
      word: Long,
      direction: Direction,
      grade: Grade,
      answer: String,
      at: Instant
  ): Unit =
    learner.reviewed(
      ItemKind.Word,
      word.toString,
      direction match {
        case Direction.Production => CardKind.Production
        case Direction.Meaning    => CardKind.Meaning
      },
      grade,
      answer,
      at,
      toConfirm = grade == Grade.Easy
    )
}

object VocabularyPractice {

  // a missed question comes back this many questions later, twice at most
  private val comesBackAfter = 4
  private val mostReturns = 2

  /** A series planned: the new words shown first; every word asked for itself,
    * then for its meaning, each in a mixed order; the new ones asked for
    * themselves once more, at the end.
    */
  def planned(words: List[SeriesWord]): List[SeriesTask] = {
    val fresh = words.filter(_.isNew)
    fresh.map(SeriesTask.Intro(_)) ++
      Random.shuffle(words.map(SeriesTask.Ask(_, Direction.Production))) ++
      Random.shuffle(words.map(SeriesTask.Ask(_, Direction.Meaning))) ++
      Random.shuffle(
        fresh.map(SeriesTask.Ask(_, Direction.Production, again = true))
      )
  }

  /** How many questions later a question missed comes back, twice at most. */
  def comesBack(missed: Boolean, attempt: Int): Option[Int] =
    Option.when(missed && attempt < mostReturns)(comesBackAfter)
}
