package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr

import com.raquo.laminar.api.L.*

/** A pop-in over the page, its `content` scrolled on its own, the page left as
  * it was behind it. Closed by its × alone, `close` then called: a stroke
  * slipping off a writing pad onto the backdrop must not close it.
  */
class Modal(heading: String, content: HtmlElement, close: () => Unit)
    extends ReactiveElement {

  override lazy val node: HtmlElement = div(
    cls := "modal-backdrop",
    div(
      cls := "modal",
      role := "dialog",
      aria.label := heading,
      div(
        cls := "modal-bar",
        span(cls := "modal-heading", heading),
        button(
          cls := "icon-button modal-close",
          aria.label := Tr("Close", "Fermer"),
          ButtonIcon(ButtonIcon.Shape.Cross).node,
          onClick --> (_ => close())
        )
      ),
      div(cls := "modal-body", content)
    )
  )
}
