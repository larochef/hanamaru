package hanamaru.frontend.model

sealed trait AppCommand

object AppCommand {

  /** Asks whether the server is ready. */
  case object CheckServer extends AppCommand
}
