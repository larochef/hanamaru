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
  def reviewed(attempt: WritingAttempt, at: Instant): Option[ReviewOutcome] =
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
        at
      )
      ReviewOutcome(grade, memory.due.toEpochMilli)
    }

  private def gradeOf(attempt: WritingAttempt): Option[Grade] =
    if (attempt.step != WritingStep.FromMemory) {
      None
    } else if (
      attempt.strokes.exists(_.judgement.verdict != StrokeVerdict.Accepted)
    ) {
      Some(Grade.Again)
    } else {
      Option.when(attempt.completed)(Grade.Good)
    }
}
