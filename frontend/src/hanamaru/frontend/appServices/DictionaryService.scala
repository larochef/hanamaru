package hanamaru.frontend.appServices

import hanamaru.frontend.api.ApiClient
import hanamaru.frontend.model.*
import hanamaru.frontend.utils.AppStreams

import com.raquo.laminar.api.L.*

class DictionaryService(bus: AppBus, apiClient: ApiClient) {

  bus.commands.events
    .collect { case command: AppCommand.LoadCoreWords => command }
    .foreach { command =>
      AppStreams.runOnce(apiClient.coreWords(command.from, command.count)) {
        case Right(words) =>
          bus.publish(AppEvent.CoreWordsLoaded(command.from, words))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    }(using unsafeWindowOwner)
}
