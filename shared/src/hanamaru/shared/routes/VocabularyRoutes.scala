package hanamaru.shared.routes

import hanamaru.shared.vocabulary.*

import sttp.tapir.*
import sttp.tapir.json.jsoniter.*

object VocabularyRoutes {

  /** A rapid series, planned: its words (those due first, then new ones) shown
    * and asked in order.
    */
  val series: PublicEndpoint[Unit, String, List[SeriesTask], Any] =
    Routes.baseRoute.get
      .in("vocabulary" / "series")
      .errorOut(Routes.refused)
      .out(jsonBody[List[SeriesTask]])

  /** Judges an answer and keeps it as a review. */
  val answer: PublicEndpoint[VocabularyAnswer, String, VocabularyVerdict, Any] =
    Routes.baseRoute.post
      .in("vocabulary" / "answers")
      .in(jsonBody[VocabularyAnswer])
      .errorOut(Routes.refused)
      .out(jsonBody[VocabularyVerdict])

  /** Keeps the learner's own word on an answer the code could not judge; and
    * says when a word missed comes back.
    */
  val selfGrade: PublicEndpoint[SelfGrade, String, ComesBack, Any] =
    Routes.baseRoute.post
      .in("vocabulary" / "self-grades")
      .in(jsonBody[SelfGrade])
      .errorOut(Routes.refused)
      .out(jsonBody[ComesBack])

  /** A sentence for a word, written by the tutor with the words the learner
    * knows and checked; none when none could be written.
    */
  val sentence: PublicEndpoint[Long, String, Option[WordSentence], Any] =
    Routes.baseRoute.get
      .in("vocabulary" / "sentence")
      .in(query[Long]("word"))
      .errorOut(Routes.refused)
      .out(jsonBody[Option[WordSentence]])
}
