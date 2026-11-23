package hanamaru.frontend.model

import hanamaru.shared.placement.PlacementAnswer
import hanamaru.shared.writing.WritingAttempt

sealed trait AppCommand

object AppCommand {

  /** Asks whether the server is ready, and again while its reference data is
    * being prepared.
    */
  case object CheckServer extends AppCommand

  final case class ShowPage(page: Page) extends AppCommand

  /** Asks for a character's strokes. */
  final case class LoadStrokes(character: String) extends AppCommand

  /** Asks where the learner stands on every kana. */
  case object LoadKanaProgress extends AppCommand

  /** Asks for the next kana to review, another than `after`. */
  final case class PickKanaToReview(after: Option[String]) extends AppCommand

  /** Asks where the kana placement test stands. */
  case object LoadPlacement extends AppCommand

  /** Starts the kana placement test, again if it was taken. */
  case object StartPlacement extends AppCommand

  final case class AnswerPlacement(answer: PlacementAnswer) extends AppCommand

  /** Keeps a character written once. */
  final case class RecordWriting(attempt: WritingAttempt) extends AppCommand
}
