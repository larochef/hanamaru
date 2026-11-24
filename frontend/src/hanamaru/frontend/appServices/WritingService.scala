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

  bus.commands.events
    .collect { case command: AppCommand.LoadManyStrokes => command }
    .foreach { command =>
      AppStreams.runOnce(apiClient.manyStrokes(command.characters)) {
        case Right(strokes) =>
          bus.publish(AppEvent.ManyStrokesLoaded(command.characters, strokes))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    }(using unsafeWindowOwner)

  // kept for tuning only: nothing waits for it
  bus.commands.events
    .collect { case command: AppCommand.RecordPadCharacters => command }
    .foreach { command =>
      AppStreams.runOnce(apiClient.recordPadCharacters(command.characters)) {
        case Right(())    => ()
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    }(using unsafeWindowOwner)

  bus.commands.events
    .collect { case command: AppCommand.RecordWriting => command }
    .foreach { command =>
      AppStreams.runOnce(apiClient.recordAttempt(command.attempt)) {
        case Right(review) =>
          bus.publish(AppEvent.WritingRecorded(command.attempt, review))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    }(using unsafeWindowOwner)
}
