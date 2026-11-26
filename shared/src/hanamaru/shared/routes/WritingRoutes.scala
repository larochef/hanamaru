package hanamaru.shared.routes

import hanamaru.shared.learner.ReviewOutcome
import hanamaru.shared.writing.*

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

  /** The strokes of several characters, those KanjiVG has. */
  val manyStrokes: PublicEndpoint[String, String, List[CharacterStrokes], Any] =
    Routes.baseRoute.get
      .in("strokes" / "many")
      .in(query[String]("characters"))
      .errorOut(Routes.refused)
      .out(jsonBody[List[CharacterStrokes]])

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

  /** Keeps a character written in a focus, every stroke drawn: material for the
    * matcher, and no review (the focus is one, once over).
    */
  val keepAttempt: PublicEndpoint[WritingAttempt, String, Unit, Any] =
    Routes.baseRoute.post
      .in("writing" / "kept")
      .in(jsonBody[WritingAttempt])
      .errorOut(Routes.refused)

  /** Keeps the kana of an answer written in the pad. */
  val recordPadCharacters
      : PublicEndpoint[List[PadCharacter], String, Unit, Any] =
    Routes.baseRoute.post
      .in("writing" / "pad")
      .in(jsonBody[List[PadCharacter]])
      .errorOut(Routes.refused)
}
