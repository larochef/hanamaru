package hanamaru.shared.routes

import hanamaru.shared.vocabulary.*

import sttp.tapir.*
import sttp.tapir.json.jsoniter.*

object VocabularyRoutes {

  /** The words of a rapid series: those due first, then new ones. */
  val series: PublicEndpoint[Unit, String, List[SeriesWord], Any] =
    Routes.baseRoute.get
      .in("vocabulary" / "series")
      .errorOut(Routes.refused)
      .out(jsonBody[List[SeriesWord]])

  /** Judges an answer and keeps it as a review. */
  val answer: PublicEndpoint[VocabularyAnswer, String, VocabularyVerdict, Any] =
    Routes.baseRoute.post
      .in("vocabulary" / "answers")
      .in(jsonBody[VocabularyAnswer])
      .errorOut(Routes.refused)
      .out(jsonBody[VocabularyVerdict])

  /** Keeps the learner's own word on an answer the code could not judge. */
  val selfGrade: PublicEndpoint[SelfGrade, String, Unit, Any] =
    Routes.baseRoute.post
      .in("vocabulary" / "self-grades")
      .in(jsonBody[SelfGrade])
      .errorOut(Routes.refused)
}
