package hanamaru.shared.routes

import hanamaru.shared.placement.*

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

  /** Where the grammar placement test stands. */
  val grammar: PublicEndpoint[Unit, String, GrammarPlacementState, Any] =
    Routes.baseRoute.get
      .in("placement" / "grammar")
      .errorOut(Routes.refused)
      .out(jsonBody[GrammarPlacementState])

  /** Starts the grammar placement test, again if it was taken before. */
  val startGrammar: PublicEndpoint[Unit, String, GrammarPlacementState, Any] =
    Routes.baseRoute.post
      .in("placement" / "grammar")
      .errorOut(Routes.refused)
      .out(jsonBody[GrammarPlacementState])

  /** Answers the grammar question asked; the test's next state. */
  val answerGrammar: PublicEndpoint[
    GrammarPlacementAnswer,
    String,
    GrammarPlacementState,
    Any
  ] =
    Routes.baseRoute.post
      .in("placement" / "grammar" / "answers")
      .in(jsonBody[GrammarPlacementAnswer])
      .errorOut(Routes.refused)
      .out(jsonBody[GrammarPlacementState])
}
