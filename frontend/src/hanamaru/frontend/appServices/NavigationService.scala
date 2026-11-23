package hanamaru.frontend.appServices

import hanamaru.frontend.model.*

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** Keeps the page shown and the browser's address together, both ways. */
class NavigationService(bus: AppBus) {

  bus.commands.events
    .collect { case AppCommand.ShowPage(page) => page }
    .foreach { page =>
      if (dom.window.location.pathname != page.path) {
        dom.window.history.pushState(null, "", page.path)
      }
      bus.publish(AppEvent.PageShown(page))
    }(using unsafeWindowOwner)

  dom.window.addEventListener(
    "popstate",
    (_: dom.PopStateEvent) => showAddressed()
  )

  /** Shows the page the browser's address names. */
  def showAddressed(): Unit =
    bus.publish(AppEvent.PageShown(Page.at(dom.window.location.pathname)))
}
