package hanamaru.frontend.model

import hanamaru.shared.ServerStatus
import hanamaru.shared.learner.*
import hanamaru.shared.placement.PlacementState
import hanamaru.shared.session.SessionState
import hanamaru.shared.writing.{CharacterStrokes, WritingAttempt}

sealed trait AppEvent

object AppEvent {

  final case class ServerChecked(status: ServerStatus) extends AppEvent

  final case class PageShown(page: Page) extends AppEvent

  final case class StrokesLoaded(strokes: CharacterStrokes) extends AppEvent

  /** A character written once was kept; written from memory, it was a review
    * too, with this outcome.
    */
  final case class WritingRecorded(
      attempt: WritingAttempt,
      review: Option[ReviewOutcome]
  ) extends AppEvent

  /** Where the kana placement test stands, after a command about it. */
  final case class PlacementUpdated(state: PlacementState) extends AppEvent

  /** Every kana reviewed at least once, where the learner stands on it. */
  final case class KanaProgressLoaded(progress: List[KanaProgress])
      extends AppEvent

  /** The next kana to review, none before any kana is met. */
  final case class KanaToReviewPicked(kana: Option[KanaToReview])
      extends AppEvent

  /** A command could not be carried out, and why, in words the page shows. */
  final case class RequestFailed(command: AppCommand, reason: String)
      extends AppEvent

  /** Where the session stands, after a command about it. */
  final case class SessionUpdated(state: SessionState) extends AppEvent
}
