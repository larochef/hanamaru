package hanamaru.shared.routes

import hanamaru.shared.questions.*

import sttp.tapir.*
import sttp.tapir.json.jsoniter.*

object QuestionRoutes {

  /** A question to the tutor, kept, with its answer if it gave one. */
  val ask: PublicEndpoint[TutorQuestion, String, AskedQuestion, Any] =
    Routes.baseRoute.post
      .in("questions")
      .in(jsonBody[TutorQuestion])
      .errorOut(Routes.refused)
      .out(jsonBody[AskedQuestion])

  /** Every question asked, the latest first. */
  val all: PublicEndpoint[Unit, String, List[AskedQuestion], Any] =
    Routes.baseRoute.get
      .in("questions")
      .errorOut(Routes.refused)
      .out(jsonBody[List[AskedQuestion]])
}
