package hanamaru.frontend.api

import hanamaru.frontend.api.airstream.AjaxBackend
import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.AppResult
import hanamaru.shared.ServerStatus
import hanamaru.shared.learner.*
import hanamaru.shared.placement.{PlacementAnswer, PlacementState}
import hanamaru.shared.routes.*
import hanamaru.shared.session.*
import hanamaru.shared.writing.{CharacterStrokes, WritingAttempt}

import scala.util.{Failure, Success}

import com.raquo.laminar.api.L.*
import sttp.client4.Backend
import sttp.model.Uri
import sttp.tapir.PublicEndpoint
import sttp.tapir.client.sttp4.SttpClientInterpreter

// Every result is an Either: failures travel as values, not stream errors.
// A stream keeps no result: restarting it sends the request again.
class ApiClient(baseUri: Uri) {
  private val backend: Backend[EventStream] = AjaxBackend()
  private val interpreter = SttpClientInterpreter()

  def status(): EventStream[AppResult[ServerStatus]] =
    call(StatusRoutes.status, ())

  def strokes(character: String): EventStream[AppResult[CharacterStrokes]] =
    call(WritingRoutes.strokes, character)

  def recordAttempt(
      attempt: WritingAttempt
  ): EventStream[AppResult[Option[ReviewOutcome]]] =
    call(WritingRoutes.recordAttempt, attempt)

  def kanaProgress(): EventStream[AppResult[List[KanaProgress]]] =
    call(LearnerRoutes.kanaProgress, ())

  def nextKana(
      after: Option[String]
  ): EventStream[AppResult[Option[KanaToReview]]] =
    call(LearnerRoutes.nextKana, after)

  def placement(): EventStream[AppResult[PlacementState]] =
    call(PlacementRoutes.kana, ())

  def startPlacement(): EventStream[AppResult[PlacementState]] =
    call(PlacementRoutes.startKana, ())

  def answerPlacement(
      answer: PlacementAnswer
  ): EventStream[AppResult[PlacementState]] =
    call(PlacementRoutes.answerKana, answer)

  def startSession(start: SessionStart): EventStream[AppResult[SessionState]] =
    call(SessionRoutes.start, start)

  def answerSession(
      session: Long,
      answer: SessionAnswer
  ): EventStream[AppResult[SessionState]] =
    call(SessionRoutes.answer, (session, answer))

  def endSession(session: Long): EventStream[AppResult[SessionState]] =
    call(SessionRoutes.end, session)

  private def call[I, O](
      endpoint: PublicEndpoint[I, String, O, Any],
      input: I
  ): EventStream[AppResult[O]] = {
    val request = interpreter
      .toRequestThrowDecodeFailures(endpoint, Some(baseUri))
      .apply(input)
    backend.send(request).recoverToTry.map {
      case Success(response) => response.body
      case Failure(error)    =>
        Left(
          Tr(
            s"The server could not be reached: ${error.getMessage}",
            s"Le serveur est injoignable : ${error.getMessage}"
          )
        )
    }
  }
}
