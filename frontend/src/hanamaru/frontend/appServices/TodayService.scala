package hanamaru.frontend.appServices

import hanamaru.frontend.api.ApiClient
import hanamaru.frontend.model.*
import hanamaru.frontend.utils.AppStreams

import com.raquo.laminar.api.L.*

class TodayService(bus: AppBus, apiClient: ApiClient) {

  bus.commands.events.foreach {
    case command @ AppCommand.LoadToday =>
      AppStreams.runOnce(apiClient.today()) {
        case Right(today) => bus.publish(AppEvent.TodayLoaded(today))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case command @ AppCommand.LoadMenu =>
      AppStreams.runOnce(apiClient.menu()) {
        case Right(menu)  => bus.publish(AppEvent.MenuLoaded(menu))
        case Left(reason) =>
          bus.publish(AppEvent.RequestFailed(command, reason))
      }
    case _ => ()
  }(using unsafeWindowOwner)
}
