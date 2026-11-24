package hanamaru.backend.writing

import hanamaru.backend.reference.ReferenceData
import hanamaru.shared.learner.ReviewOutcome
import hanamaru.shared.routes.WritingRoutes
import hanamaru.shared.writing.{CharacterStrokes, WritingAttempt}

import java.time.Instant

import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

class WritingApi(
    reference: ReferenceData,
    attempts: WritingAttempts,
    padCharacters: PadCharacters,
    review: WritingReview
) {

  def strokes(character: String): Either[String, CharacterStrokes] =
    reference.kanjiVg match {
      case None          => Left("The reference data is not ready")
      case Some(archive) =>
        archive
          .strokes(character)
          .toRight(s"KanjiVG has no strokes for $character")
    }

  def manyStrokes(characters: String): Either[String, List[CharacterStrokes]] =
    reference.kanjiVg
      .toRight("The reference data is not ready")
      .map(archive =>
        characters.codePoints.toArray.toList.distinct
          .flatMap(point => archive.strokes(String(Character.toChars(point))))
      )

  def recorded(attempt: WritingAttempt): Option[ReviewOutcome] = {
    attempts.record(attempt)
    review.reviewed(attempt, Instant.now())
  }

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    WritingRoutes.strokes.handle(strokes),
    WritingRoutes.manyStrokes.handle(manyStrokes),
    WritingRoutes.recordAttempt.handleSuccess(recorded),
    WritingRoutes.recordPadCharacters.handleSuccess(padCharacters.record)
  )
}
