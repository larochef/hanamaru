package hanamaru.frontend.components

import com.raquo.laminar.api.L.*

/** A sign drawn for a button (+, −, ×, arrows, the writing tools), centred in
  * its box whatever the font, in the button's colour.
  */
class ButtonIcon(shape: ButtonIcon.Shape) extends ReactiveElement {

  private def path: String = shape match {
    case ButtonIcon.Shape.Plus     => "M2 8h12M8 2v12"
    case ButtonIcon.Shape.Minus    => "M2 8h12"
    case ButtonIcon.Shape.Cross    => "M3 3l10 10M13 3L3 13"
    case ButtonIcon.Shape.Previous => "M10 3L5 8l5 5"
    case ButtonIcon.Shape.Next     => "M6 3l5 5-5 5"
    case ButtonIcon.Shape.Down     => "M3 6l5 5 5-5"
    // a speech bubble
    case ButtonIcon.Shape.Chat => "M2.5 2.5h11v8H7.5l-3 3v-3h-2z"
    // an arrow going back: the last stroke taken away
    case ButtonIcon.Shape.Undo =>
      "M5.5 9.5L2 6l3.5-3.5M2 6h7.5a3.5 3.5 0 0 1 0 7H7"
    // an eraser: the whole drawing rubbed out
    case ButtonIcon.Shape.Eraser =>
      "M10 2l4 4-6.5 6.5H4L1.5 10zM5.5 6.5l4 4M8 14.5h6.5"
    // the key that takes away the last character
    case ButtonIcon.Shape.Backspace =>
      "M15 3H5.5L1 8l4.5 5H15zM12.5 5.5l-5 5M7.5 5.5l5 5"
    // an arrow to a bar: on to the next character
    case ButtonIcon.Shape.NextCharacter =>
      "M1.5 8h9M7.5 4.5L11 8l-3.5 3.5M14 3v10"
    case ButtonIcon.Shape.Pen =>
      "M2.5 13.5l3-.7 8-8-2.3-2.3-8 8zM9.8 3.9l2.3 2.3"
    case ButtonIcon.Shape.Keyboard =>
      "M1 3.5h14v9H1zM4 6.5h.5M7.75 6.5h.5M11.5 6.5h.5M4.5 9.5h7"
    case ButtonIcon.Shape.Check => "M2.5 8.5l3.5 3.5 7.5-8"
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
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"
      )
    )
  )
}

object ButtonIcon {
  enum Shape {
    case Plus, Minus, Cross, Previous, Next, Down, Chat, Undo, Eraser,
      Backspace, NextCharacter, Pen, Keyboard, Check
  }
}
