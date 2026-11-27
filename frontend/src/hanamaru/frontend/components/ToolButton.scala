package hanamaru.frontend.components

import com.raquo.laminar.api.L.*

/** A writing tool's button: an icon (`shape`) or a sign (`sign`), what it does
  * said to the eye and to the ear (`label`). It never takes the focus, so that
  * the field it changes keeps its selection, and the screen's keyboard stays as
  * it was.
  */
class ToolButton(
    face: ToolButton.Face,
    label: String,
    action: () => Unit,
    enabled: Signal[Boolean] = Val(true)
) extends ReactiveElement {

  override lazy val node: HtmlElement = button(
    typ := "button",
    cls := "writing-tool",
    title := label,
    aria.label := label,
    disabled <-- enabled.map(!_),
    onMouseDown.preventDefault --> (_ => ()),
    onClick --> (_ => action()),
    face match {
      case ToolButton.Face.Icon(shape) => ButtonIcon(shape).node
      case ToolButton.Face.Sign(sign)  => span(lang := "ja", sign)
      case ToolButton.Face.Mark(mark)  =>
        span(cls := "tool-mark", lang := "ja", mark)
    }
  )
}

object ToolButton {

  /** What a tool's button shows. */
  enum Face {
    case Icon(shape: ButtonIcon.Shape)
    case Sign(sign: String)

    /** A mark set on a kana (゛, ゜), drawn larger than a sign: alone, it is
      * small, high in its box.
      */
    case Mark(mark: String)
  }
}
