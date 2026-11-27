package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr

import com.raquo.laminar.api.L.*

/** How the learner answers, as they choose (`stylus`): with the stylus, or the
  * keyboard; the one chosen filled.
  */
class InputChoice(stylus: Var[Boolean]) extends ReactiveElement {

  private def choice(
      shape: ButtonIcon.Shape,
      label: String,
      withStylus: Boolean
  ) = button(
    typ := "button",
    cls("is-chosen") <-- stylus.signal.map(_ == withStylus),
    aria.pressed <-- stylus.signal.map(on => (on == withStylus).toString),
    onMouseDown.preventDefault --> (_ => ()),
    onClick --> (_ => stylus.set(withStylus)),
    ButtonIcon(shape).node,
    label
  )

  override lazy val node: HtmlElement = div(
    cls := "input-choice",
    choice(ButtonIcon.Shape.Pen, Tr("Stylus", "Stylet"), withStylus = true),
    choice(
      ButtonIcon.Shape.Keyboard,
      Tr("Keyboard", "Clavier"),
      withStylus = false
    )
  )
}
