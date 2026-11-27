package hanamaru.frontend.components

import hanamaru.frontend.components.KanaInput.Mode
import hanamaru.frontend.i18n.Tr

import com.raquo.laminar.api.L.*

/** What is written, chosen in one touch: hiragana, katakana or kanji, each in
  * its colour, the one chosen filled; those that do not serve here
  * (`available`) left in their place, faded, so that nothing moves from one
  * exercise to the next. Beside the box written in (`layout`, a column), or
  * under the field typed in (a row), where kanji are said to be written with
  * the stylus.
  */
class ScriptModes(
    mode: Var[Mode],
    available: Set[Mode],
    layout: ScriptModes.Layout,
    picked: () => Unit = () => ()
) extends ReactiveElement {

  private def choice(each: Mode) = {
    val usable = available.contains(each)
    button(
      typ := "button",
      cls := "script-mode",
      cls := Mode.className(each),
      cls("is-chosen") <-- mode.signal.map(_ == each),
      aria.pressed <-- mode.signal.map(now => (now == each).toString),
      disabled := !usable,
      onMouseDown.preventDefault --> (_ => ()),
      onClick --> { _ =>
        mode.set(each)
        picked()
      },
      span(cls := "script-mode-sign", lang := "ja", each.sign),
      span(
        cls := "script-mode-name",
        if (!usable && each == Mode.Kanji && layout == ScriptModes.Layout.Row)
          Tr("with the stylus", "au stylet")
        else each.name
      )
    )
  }

  override lazy val node: HtmlElement = div(
    cls := "script-modes",
    cls := (layout match {
      case ScriptModes.Layout.Column => "is-column"
      case ScriptModes.Layout.Row    => "is-row"
    }),
    Option.when(layout == ScriptModes.Layout.Column)(
      span(cls := "script-modes-label", Tr("recognises", "reconnaît"))
    ),
    Mode.values.toList.map(choice)
  )
}

object ScriptModes {

  /** Where the modes stand: beside the box, or under the field. */
  enum Layout {
    case Column, Row
  }
}
