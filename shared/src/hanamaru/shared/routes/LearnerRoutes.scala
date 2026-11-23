package hanamaru.shared.routes

import hanamaru.shared.learner.{KanaProgress, KanaToReview}

import sttp.tapir.*
import sttp.tapir.json.jsoniter.*

object LearnerRoutes {

  /** Every kana reviewed at least once: its status, and when it is due. */
  val kanaProgress: PublicEndpoint[Unit, String, List[KanaProgress], Any] =
    Routes.baseRoute.get
      .in("learner" / "kana")
      .errorOut(Routes.refused)
      .out(jsonBody[List[KanaProgress]])

  /** The next kana to review, another than `after`; none before any kana is
    * met.
    */
  val nextKana
      : PublicEndpoint[Option[String], String, Option[KanaToReview], Any] =
    Routes.baseRoute.get
      .in("learner" / "kana" / "next")
      .in(query[Option[String]]("after"))
      .errorOut(Routes.refused)
      .out(jsonBody[Option[KanaToReview]])
}
