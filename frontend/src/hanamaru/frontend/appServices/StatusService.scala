package hanamaru.frontend.appServices

import hanamaru.frontend.api.ApiClient
import hanamaru.frontend.model.*
import hanamaru.frontend.utils.AppStreams
import hanamaru.shared.{ModelState, ReferenceDataState}

import scala.scalajs.js.timers.setTimeout

import com.raquo.laminar.api.L.*

class StatusService(bus: AppBus, apiClient: ApiClient) {
  private val pollMillis = 1000

  bus.commands.events
    .collect { case command @ AppCommand.CheckServer => command }
    .foreach(check)(using unsafeWindowOwner)

  private def check(command: AppCommand): Unit = {
    AppStreams.runOnce(apiClient.status()) {
      case Right(status) =>
        bus.publish(AppEvent.ServerChecked(status))
        if (
          status.strokes == ReferenceDataState.Preparing ||
          status.dictionary == ReferenceDataState.Preparing ||
          status.analyser == ReferenceDataState.Preparing ||
          status.model == ModelState.Starting
        ) {
          setTimeout(pollMillis)(check(command))
        }
      case Left(reason) => bus.publish(AppEvent.RequestFailed(command, reason))
    }
  }
}
