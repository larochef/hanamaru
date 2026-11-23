package hanamaru.shared.routes

import hanamaru.shared.session.*

import sttp.tapir.*
import sttp.tapir.json.jsoniter.*

object SessionRoutes {

  /** Starts an Express session; its first exercise. */
  val start: PublicEndpoint[SessionStart, String, SessionState, Any] =
    Routes.baseRoute.post
      .in("sessions")
      .in(jsonBody[SessionStart])
      .errorOut(Routes.refused)
      .out(jsonBody[SessionState])

  /** Answers the exercise asked; the next one, none once time is up. */
  val answer: PublicEndpoint[(Long, SessionAnswer), String, SessionState, Any] =
    Routes.baseRoute.post
      .in("sessions" / path[Long]("session") / "answers")
      .in(jsonBody[SessionAnswer])
      .errorOut(Routes.refused)
      .out(jsonBody[SessionState])

  /** Ends a session before its time; what it did. */
  val end: PublicEndpoint[Long, String, SessionState, Any] =
    Routes.baseRoute.post
      .in("sessions" / path[Long]("session") / "end")
      .errorOut(Routes.refused)
      .out(jsonBody[SessionState])
}
