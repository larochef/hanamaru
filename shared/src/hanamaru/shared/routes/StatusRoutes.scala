package hanamaru.shared.routes

import hanamaru.shared.ServerStatus

import sttp.tapir.*
import sttp.tapir.json.jsoniter.*

object StatusRoutes {

  /** Whether the server is ready to serve the learner. */
  val status: PublicEndpoint[Unit, String, ServerStatus, Any] =
    Routes.baseRoute.get
      .in("status")
      .errorOut(Routes.refused)
      .out(jsonBody[ServerStatus])
}
