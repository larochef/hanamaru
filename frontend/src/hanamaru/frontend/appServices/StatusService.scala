package hanamaru.frontend.appServices

import hanamaru.frontend.api.ApiClient
import hanamaru.frontend.model.*
import hanamaru.frontend.utils.AppStreams

import com.raquo.laminar.api.L.*

class StatusService(bus: AppBus, apiClient: ApiClient) {

  bus.commands.events
    .collect { case command @ AppCommand.CheckServer => command }
    .foreach { command =>
      AppStreams.runOnce(apiClient.status()) {
        case Right(status) => bus.publish(AppEvent.ServerChecked(status))
        case Left(reason)  =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    }(using unsafeWindowOwner)
}
