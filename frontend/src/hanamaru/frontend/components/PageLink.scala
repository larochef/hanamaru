package hanamaru.frontend.components

import hanamaru.frontend.model.*

import com.raquo.laminar.api.L.*

/** A link to one of the application's pages, shown without reloading. */
class PageLink(bus: AppBus, page: Page, content: Modifier[HtmlElement]*)
    extends ReactiveElement {

  override lazy val node: HtmlElement = a(
    href := page.path,
    onClick.preventDefault.mapTo(AppCommand.ShowPage(page)) --> (bus.send(_)),
    content
  )
}
