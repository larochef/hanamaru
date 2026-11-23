package hanamaru.shared.routes

import hanamaru.shared.writing.CharacterStrokes

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
}
