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

  /** A kana's reading judged, and kept as a review. */
  val kanaReading
      : PublicEndpoint[KanaReadingAnswer, String, ReadingResult, Any] =
    Routes.baseRoute.post
      .in("characters" / "kana-readings")
      .in(jsonBody[KanaReadingAnswer])
      .errorOut(Routes.refused)
      .out(jsonBody[ReadingResult])
}
