package hanamaru.frontend.model

import hanamaru.shared.ServerStatus

sealed trait AppEvent

object AppEvent {

  final case class ServerChecked(status: ServerStatus) extends AppEvent

  /** A command could not be carried out, and why, in words the page shows. */
  final case class RequestFailed(command: AppCommand, reason: String)
      extends AppEvent
}
