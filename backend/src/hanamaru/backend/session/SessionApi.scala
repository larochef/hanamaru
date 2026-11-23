package hanamaru.backend.session

import hanamaru.shared.routes.SessionRoutes

import java.time.Instant

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

class SessionApi(express: ExpressSessions) {

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    SessionRoutes.start.handle(start => express.started(start, Instant.now())),
    SessionRoutes.answer.handle((id, answer) =>
      express.answered(id, answer, Instant.now())
    ),
    SessionRoutes.end.handle(id => express.ended(id, Instant.now()))
  )
}
