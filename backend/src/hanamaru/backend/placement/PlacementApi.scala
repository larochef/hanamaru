package hanamaru.backend.placement

import hanamaru.shared.routes.PlacementRoutes

import java.time.Instant

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

class PlacementApi(kana: KanaPlacementTest) {

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    PlacementRoutes.kana.handleSuccess(_ => kana.state()),
    PlacementRoutes.startKana.handleSuccess(_ => kana.started(Instant.now())),
    PlacementRoutes.answerKana.handle(answer =>
      kana.answered(answer, Instant.now())
    )
  )
}
