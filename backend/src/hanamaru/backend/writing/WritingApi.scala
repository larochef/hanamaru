package hanamaru.backend.writing

import hanamaru.backend.reference.ReferenceData
import hanamaru.shared.routes.WritingRoutes
import hanamaru.shared.writing.CharacterStrokes

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

class WritingApi(reference: ReferenceData, attempts: WritingAttempts) {

  def strokes(character: String): Either[String, CharacterStrokes] =
    reference.kanjiVg match {
      case None          => Left("The reference data is not ready")
      case Some(archive) =>
        archive
          .strokes(character)
          .toRight(s"KanjiVG has no strokes for $character")
    }

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    WritingRoutes.strokes.handle(strokes),
    WritingRoutes.recordAttempt.handleSuccess(attempts.record)
  )
}
