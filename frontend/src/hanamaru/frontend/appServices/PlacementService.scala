package hanamaru.frontend.appServices

import hanamaru.frontend.api.ApiClient
import hanamaru.frontend.model.*
import hanamaru.frontend.utils.AppStreams
import hanamaru.shared.placement.PlacementState

import com.raquo.laminar.api.L.*

class PlacementService(bus: AppBus, apiClient: ApiClient) {

  bus.commands.events
    .collect {
      case command @ AppCommand.LoadPlacement =>
        command -> apiClient.placement()
      case command @ AppCommand.StartPlacement =>
        command -> apiClient.startPlacement()
      case command @ AppCommand.AnswerPlacement(answer) =>
        command -> apiClient.answerPlacement(answer)
    }
    .foreach((command, request) => answered(command, request))(using
      unsafeWindowOwner
    )

  private def answered(
      command: AppCommand,
      request: EventStream[AppResult[PlacementState]]
  ): Unit =
    AppStreams.runOnce(request) {
      case Right(state) => bus.publish(AppEvent.PlacementUpdated(state))
      case Left(reason) => bus.publish(AppEvent.RequestFailed(command, reason))
    }
}
