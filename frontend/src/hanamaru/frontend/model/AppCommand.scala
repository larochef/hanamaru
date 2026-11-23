package hanamaru.frontend.model

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

  /** Keeps a character written once. */
  final case class RecordWriting(attempt: WritingAttempt) extends AppCommand
}
