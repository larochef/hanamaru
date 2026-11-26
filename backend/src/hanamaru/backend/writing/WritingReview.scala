package hanamaru.backend.writing

import hanamaru.backend.learner.*
import hanamaru.shared.kana.Kana
import hanamaru.shared.learner.{Grade, ReviewOutcome}
import hanamaru.shared.writing.*

import java.time.Instant

import com.github.plokhotnyuk.jsoniter_scala.core.writeToString

/** A kana written from memory, as a review of its writing card: graded by its
  * strokes, not by the learner.
  */
final class WritingReview(learner: LearnerStore) {

  /** The review an attempt makes, if it makes one: an attempt written from
    * memory to its end, or stopped by a wrong stroke. Guided writing is
    * practice, and an attempt given up is no answer.
    */
  def reviewed(
      attempt: WritingAttempt,
      at: Instant,
      studySession: Option[Long] = None
  ): Option[ReviewOutcome] =
    for {
      kana <- Kana.find(attempt.character)
      grade <- gradeOf(attempt)
    } yield {
      val memory = learner.reviewed(
        ItemKind.Kana,
        kana.character,
        CardKind.Writing,
        grade,
        writeToString(attempt),
        at,
        studySession = studySession
      )
      ReviewOutcome(grade, memory.due.toEpochMilli)
    }

  /** A kana written within a word, as a review of its writing: one written
    * wrong (or with strokes out of order) is always kept, so that it comes back
    * to be practised; one written right only when its writing was due, not to
    * crowd its schedule with the words it is in.
    */
  def writtenInWord(attempt: WritingAttempt, at: Instant): Unit =
    for {
      kana <- Kana.find(attempt.character)
      grade <- gradeOf(attempt)
      if grade != Grade.Good || writingDue(kana.character, at)
    } {
      learner.reviewed(
        ItemKind.Kana,
        kana.character,
        CardKind.Writing,
        grade,
        writeToString(attempt),
        at
      )
    }

  private def writingDue(character: String, at: Instant): Boolean =
    learner
      .kanaCards()
      .exists(card =>
        card.character == character && card.kind == CardKind.Writing &&
          card.due <= at.toEpochMilli
      )

  /** Writing from memory, graded by how it went: right, good; right but for the
    * order or direction of some strokes, hard; wrong, or not remembered, again.
    * Guided writing is practice, not a review.
    */
  def gradeOf(attempt: WritingAttempt): Option[Grade] =
    Option.when(attempt.step == WritingStep.FromMemory)(
      WritingCorrection.outcome(attempt) match {
        case WritingOutcome.Right       => Grade.Good
        case WritingOutcome.StrokeOrder => Grade.Hard
        case WritingOutcome.Wrong       => Grade.Again
      }
    )
}
