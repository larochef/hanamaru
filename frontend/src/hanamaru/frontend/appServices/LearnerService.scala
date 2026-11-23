package hanamaru.frontend.appServices

import hanamaru.frontend.api.ApiClient
import hanamaru.frontend.model.*
import hanamaru.frontend.utils.AppStreams

import com.raquo.laminar.api.L.*

class LearnerService(bus: AppBus, apiClient: ApiClient) {

  bus.commands.events
    .collect { case command @ AppCommand.LoadKanaProgress => command }
    .foreach { command =>
      AppStreams.runOnce(apiClient.kanaProgress()) {
        case Right(progress) =>
          bus.publish(AppEvent.KanaProgressLoaded(progress))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    }(using unsafeWindowOwner)

  bus.commands.events
    .collect { case command: AppCommand.PickKanaToReview => command }
    .foreach { command =>
      AppStreams.runOnce(apiClient.nextKana(command.after)) {
        case Right(kana)  => bus.publish(AppEvent.KanaToReviewPicked(kana))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    }(using unsafeWindowOwner)
}
