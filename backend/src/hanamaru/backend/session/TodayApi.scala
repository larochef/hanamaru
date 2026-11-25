package hanamaru.backend.session

import hanamaru.shared.routes.TodayRoutes

import java.time.Instant

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

class TodayApi(today: Today) {

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    TodayRoutes.today.handleSuccess(_ => today.today(Instant.now())),
    TodayRoutes.menu.handleSuccess(_ => today.menu(Instant.now()))
  )
}
