package hanamaru.shared.routes

import hanamaru.shared.learner.ReviewOutcome
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
    * material the matcher's tolerances are tuned on. Written from memory, it is
    * also a review of the character's writing: its outcome, if it made one.
    */
  val recordAttempt
      : PublicEndpoint[WritingAttempt, String, Option[ReviewOutcome], Any] =
    Routes.baseRoute.post
      .in("writing" / "attempts")
      .in(jsonBody[WritingAttempt])
      .errorOut(Routes.refused)
      .out(jsonBody[Option[ReviewOutcome]])
}
