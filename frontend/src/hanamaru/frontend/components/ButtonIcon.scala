package hanamaru.frontend.components

import com.raquo.laminar.api.L.*

/** A sign drawn for a button (+, −, ×, arrows), centred in its box whatever the
  * font, in the button's colour.
  */
class ButtonIcon(shape: ButtonIcon.Shape) extends ReactiveElement {

  private def path: String = shape match {
    case ButtonIcon.Shape.Plus     => "M2 8h12M8 2v12"
    case ButtonIcon.Shape.Minus    => "M2 8h12"
    case ButtonIcon.Shape.Cross    => "M3 3l10 10M13 3L3 13"
    case ButtonIcon.Shape.Previous => "M10 3L5 8l5 5"
    case ButtonIcon.Shape.Next     => "M6 3l5 5-5 5"
    case ButtonIcon.Shape.Down     => "M3 6l5 5 5-5"
  }

  override lazy val node: HtmlElement = span(
    cls := "button-icon",
    aria.hidden := true,
    svg.svg(
      svg.viewBox := "0 0 16 16",
      svg.path(
        svg.d := path,
        svg.fill := "none",
        svg.stroke := "currentColor",
        svg.strokeWidth := "1.8",
        svg.strokeLineCap := "round"
      )
    )
  )
}

object ButtonIcon {
  enum Shape {
    case Plus, Minus, Cross, Previous, Next, Down
  }
}
