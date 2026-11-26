package hanamaru.shared.routes

import hanamaru.shared.characters.*
import hanamaru.shared.placement.ReadingResult

import sttp.tapir.*
import sttp.tapir.json.jsoniter.*

object CharacterRoutes {

  /** A lesson session's kana and kanji: those due, then the unit's new ones. */
  val practice: PublicEndpoint[Unit, String, List[CharacterExercise], Any] =
    Routes.baseRoute.get
      .in("characters")
      .errorOut(Routes.refused)
      .out(jsonBody[List[CharacterExercise]])

  /** A unit's kanji gone over out of turn, from its page. */
  val ofUnit: PublicEndpoint[String, String, List[CharacterExercise], Any] =
    Routes.baseRoute.get
      .in("characters" / "unit" / path[String]("point"))
      .errorOut(Routes.refused)
      .out(jsonBody[List[CharacterExercise]])

  /** The next card of a focus on a new character, none once it is over. */
  val focusNext
      : PublicEndpoint[FocusState, String, Option[CharacterExercise], Any] =
    Routes.baseRoute.post
      .in("characters" / "focus" / "next")
      .in(jsonBody[FocusState])
      .errorOut(Routes.refused)
      .out(jsonBody[Option[CharacterExercise]])

  /** A focus over: the character's reading and writing kept as reviewed. */
  val focusDone: PublicEndpoint[FocusState, String, Unit, Any] =
    Routes.baseRoute.post
      .in("characters" / "focus" / "done")
      .in(jsonBody[FocusState])
      .errorOut(Routes.refused)

  /** A kana's reading judged, and kept as a review. */
  val kanaReading
      : PublicEndpoint[KanaReadingAnswer, String, ReadingResult, Any] =
    Routes.baseRoute.post
      .in("characters" / "kana-readings")
      .in(jsonBody[KanaReadingAnswer])
      .errorOut(Routes.refused)
      .out(jsonBody[ReadingResult])
}
