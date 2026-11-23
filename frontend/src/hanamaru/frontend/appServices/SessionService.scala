package hanamaru.frontend.appServices

import hanamaru.frontend.api.ApiClient
import hanamaru.frontend.model.*
import hanamaru.frontend.utils.AppStreams
import hanamaru.shared.session.SessionState

import com.raquo.laminar.api.L.*

class SessionService(bus: AppBus, apiClient: ApiClient) {

  bus.commands.events
    .collect {
      case command @ AppCommand.StartSession(start) =>
        command -> apiClient.startSession(start)
      case command @ AppCommand.AnswerSession(session, answer) =>
        command -> apiClient.answerSession(session, answer)
      case command @ AppCommand.EndSession(session) =>
        command -> apiClient.endSession(session)
    }
    .foreach((command, request) => answered(command, request))(using
      unsafeWindowOwner
    )

  private def answered(
      command: AppCommand,
      request: EventStream[AppResult[SessionState]]
  ): Unit =
    AppStreams.runOnce(request) {
      case Right(state) => bus.publish(AppEvent.SessionUpdated(state))
      case Left(reason) => bus.publish(AppEvent.RequestFailed(command, reason))
    }
}
