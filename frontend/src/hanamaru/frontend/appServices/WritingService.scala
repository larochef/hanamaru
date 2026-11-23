package hanamaru.frontend.appServices

import hanamaru.frontend.api.ApiClient
import hanamaru.frontend.model.*
import hanamaru.frontend.utils.AppStreams

import com.raquo.laminar.api.L.*

class WritingService(bus: AppBus, apiClient: ApiClient) {

  bus.commands.events
    .collect { case command: AppCommand.LoadStrokes => command }
    .foreach { command =>
      AppStreams.runOnce(apiClient.strokes(command.character)) {
        case Right(strokes) => bus.publish(AppEvent.StrokesLoaded(strokes))
        case Left(reason)   =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    }(using unsafeWindowOwner)
}
