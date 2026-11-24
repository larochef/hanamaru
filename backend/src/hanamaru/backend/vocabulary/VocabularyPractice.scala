package hanamaru.backend.vocabulary

import hanamaru.backend.learner.*
import hanamaru.backend.lessons.Units
import hanamaru.backend.reference.Dictionary
import hanamaru.backend.writing.{WritingAttempts, WritingReview}
import hanamaru.shared.learner.Grade
import hanamaru.shared.vocabulary.*

import java.time.Instant

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
    * word comes until the unit is acquired, its lesson included.
    */
  def series(at: Instant): Either[String, List[SeriesWord]] = dictionary.map {
    words =>
      val due = learner
        .dueKeys(ItemKind.Word, at.toEpochMilli, largestSeries)
        .flatMap(_.toLongOption)
      val met = learner.metKeys(ItemKind.Word).flatMap(_.toLongOption)
      val newCount = (largestSeries - due.size).min(mostNew).max(0)
      val dueWords = words.words(due)
      val fresh = units.current match {
        case Some((_, unitWords)) =>
          val unmet = unitWords.filterNot(word => met(word.id))
          val unsettled = unitWords.filter(word =>
            met(word.id) && !due.contains(word.id) && !stable(word.id)
          )
          unmet.take(newCount).map(SeriesWord(_, isNew = true)) ++
            unsettled
              .take((newCount - unmet.size).max(0))
              .map(SeriesWord(_, isNew = false))
        case None =>
          words.nextWords(met, newCount).map(SeriesWord(_, isNew = true))
      }
      dueWords.map(word =>
        SeriesWord(word, isNew = false, inSentence = stable(word.id))
      ) ++ fresh
  }

  // A word is asked in a sentence once its production has passed its first
  // steps: context too early takes attention from the word itself.
  private def stable(word: Long): Boolean = units.stable(word)

  /** A sentence for a word, made of the words the learner has met. */
  def sentence(word: Long): Either[String, Option[WordSentence]] =
    dictionary.map { words =>
      val met =
        learner.metKeys(ItemKind.Word).flatMap(_.toLongOption).filter(_ != word)
      words
        .words(List(word))
        .headOption
        .flatMap(asked => sentences.written(asked, words.words(met.toList)))
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
      VocabularyVerdict(verdict, grade, tutorMatched)
    }

  /** The learner's own word on an answer neither the code nor the tutor could
    * judge.
    */
  def selfGraded(grade: SelfGrade, at: Instant): Unit =
    reviewed(
      grade.word,
      grade.direction,
      if (grade.right) Grade.Good else Grade.Again,
      writeToString(grade),
      at
    )

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
