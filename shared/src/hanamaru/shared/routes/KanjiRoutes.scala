package hanamaru.shared.routes

import hanamaru.shared.kanji.*
import hanamaru.shared.learner.ReviewOutcome
import hanamaru.shared.writing.WritingAttempt

import sttp.tapir.*
import sttp.tapir.json.jsoniter.*

object KanjiRoutes {

  /** The kanji of a school grade, the most frequent first. */
  val grade: PublicEndpoint[Int, String, List[Kanji], Any] =
    Routes.baseRoute.get
      .in("kanji")
      .in(query[Int]("grade"))
      .errorOut(Routes.refused)
      .out(jsonBody[List[Kanji]])

  /** A kanji, with words written with it. */
  val kanji: PublicEndpoint[String, String, KanjiDetail, Any] =
    Routes.baseRoute.get
      .in("kanji" / path[String]("literal"))
      .errorOut(Routes.refused)
      .out(jsonBody[KanjiDetail])

  /** A kanji's reading in a word judged, and kept as a review. */
  val reading
      : PublicEndpoint[KanjiReadingAnswer, String, KanjiReadingVerdict, Any] =
    Routes.baseRoute.post
      .in("kanji-practice" / "readings")
      .in(jsonBody[KanjiReadingAnswer])
      .errorOut(Routes.refused)
      .out(jsonBody[KanjiReadingVerdict])

  /** A kanji written from memory, kept as a review of its writing (none for one
    * written guided).
    */
  val writing
      : PublicEndpoint[WritingAttempt, String, Option[ReviewOutcome], Any] =
    Routes.baseRoute.post
      .in("kanji-practice" / "writings")
      .in(jsonBody[WritingAttempt])
      .errorOut(Routes.refused)
      .out(jsonBody[Option[ReviewOutcome]])
}
