package hanamaru.shared.routes

import hanamaru.shared.placement.{PlacementAnswer, PlacementState}

import sttp.tapir.*
import sttp.tapir.json.jsoniter.*

object PlacementRoutes {

  /** Where the kana placement test stands. */
  val kana: PublicEndpoint[Unit, String, PlacementState, Any] =
    Routes.baseRoute.get
      .in("placement" / "kana")
      .errorOut(Routes.refused)
      .out(jsonBody[PlacementState])

  /** Starts the kana placement test, again if it was taken before. */
  val startKana: PublicEndpoint[Unit, String, PlacementState, Any] =
    Routes.baseRoute.post
      .in("placement" / "kana")
      .errorOut(Routes.refused)
      .out(jsonBody[PlacementState])

  /** Answers the question asked; the test's next state. */
  val answerKana: PublicEndpoint[PlacementAnswer, String, PlacementState, Any] =
    Routes.baseRoute.post
      .in("placement" / "kana" / "answers")
      .in(jsonBody[PlacementAnswer])
      .errorOut(Routes.refused)
      .out(jsonBody[PlacementState])
}
