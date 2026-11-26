package hanamaru.shared.routes

import hanamaru.shared.vocabulary.*

import sttp.tapir.*
import sttp.tapir.json.jsoniter.*

object VocabularyRoutes {

  // which words, in the address: `?free=true`, `?unit=<point>`, or neither
  private val seriesOf: EndpointInput[SeriesOf] =
    query[Boolean]("free")
      .default(false)
      .and(query[Option[String]]("unit"))
      .map((free, unit) =>
        unit.fold(if (free) SeriesOf.Free else SeriesOf.MyLesson)(
          SeriesOf.OfUnit(_)
        )
      ) {
        case SeriesOf.MyLesson      => (false, None)
        case SeriesOf.Free          => (true, None)
        case SeriesOf.OfUnit(point) => (false, Some(point))
      }

  /** A rapid series, planned: its words ([[SeriesOf]]) shown and asked in
    * order.
    */
  val series: PublicEndpoint[SeriesOf, String, List[SeriesTask], Any] =
    Routes.baseRoute.get
      .in("vocabulary" / "series")
      .in(seriesOf)
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
