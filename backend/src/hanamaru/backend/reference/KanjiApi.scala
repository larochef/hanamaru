package hanamaru.backend.reference

import hanamaru.shared.kanji.{Kanji, KanjiDetail}
import hanamaru.shared.routes.KanjiRoutes

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

class KanjiApi(reference: ReferenceData) {
  private val wordsShown = 12

  def ofGrade(grade: Int): Either[String, List[Kanji]] =
    reference.kanji.toRight("The kanji are not ready").map(_.ofGrade(grade))

  def detail(literal: String): Either[String, KanjiDetail] =
    for {
      book <- reference.kanji.toRight("The kanji are not ready")
      kanji <- book.kanji(literal).toRight(s"No kanji $literal")
    } yield KanjiDetail(
      kanji,
      reference.dictionary.fold(Nil)(_.writtenWith(literal, wordsShown))
    )

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    KanjiRoutes.grade.handle(ofGrade),
    KanjiRoutes.kanji.handle(detail)
  )
}
