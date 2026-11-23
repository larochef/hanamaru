package hanamaru.shared.routes

import hanamaru.shared.writing.{CharacterStrokes, WritingAttempt}

import sttp.tapir.*
import sttp.tapir.json.jsoniter.*

object WritingRoutes {

  /** A character's strokes, in the order they are written. */
  val strokes: PublicEndpoint[String, String, CharacterStrokes, Any] =
    Routes.baseRoute.get
      .in("strokes")
      .in(query[String]("character"))
      .errorOut(Routes.refused)
      .out(jsonBody[CharacterStrokes])

  /** Keeps a character written once, every stroke drawn with its verdict: the
    * material the matcher's tolerances are tuned on.
    */
  val recordAttempt: PublicEndpoint[WritingAttempt, String, Unit, Any] =
    Routes.baseRoute.post
      .in("writing" / "attempts")
      .in(jsonBody[WritingAttempt])
      .errorOut(Routes.refused)
}
