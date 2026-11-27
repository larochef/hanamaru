package hanamaru.frontend.components

import hanamaru.frontend.components.KanaInput.Mode
import hanamaru.frontend.i18n.Tr

import com.raquo.laminar.api.L.*

/** The box written in with the stylus, in one frame with what acts on what is
  * drawn there, and nothing else: the last stroke taken away, the whole drawing
  * rubbed out, the drawing taken as it is (on to the next character); and,
  * beside it, what it recognises (`mode`, among `available`), the frame in its
  * colour. What acts on the answer is the answer's, not the box's.
  */
class WritingBox(pad: KanaPad, mode: Var[Mode], available: Set[Mode])
    extends ReactiveElement {

  override lazy val node: HtmlElement = div(
    cls := "writing-box-block",
    p(cls := "frame-label", Tr("Writing box", "Case d'écriture")),
    div(
      cls := "writing-box-frame",
      cls <-- mode.signal.map(Mode.className),
      div(
        cls := "writing-box-main",
        pad.node,
        div(
          cls := "writing-box-tools",
          ToolButton(
            ToolButton.Face.Icon(ButtonIcon.Shape.Undo),
            Tr("Take away the last stroke", "Annuler le dernier trait"),
            () => pad.undo(),
            pad.hasDrawn
          ).node,
          ToolButton(
            ToolButton.Face.Icon(ButtonIcon.Shape.Eraser),
            Tr(
              "Rub out the whole drawing",
              "Effacer tout le tracé de la case"
            ),
            () => pad.clear(),
            pad.hasDrawn
          ).node,
          span(
            cls := "writing-box-mode",
            lang := "ja",
            child.text <-- mode.signal.map(now => s"${now.sign} ${now.name}")
          ),
          button(
            typ := "button",
            cls := "writing-tool next-character",
            title := Tr(
              "Take the drawing as it is: on to the next character",
              "Prendre le tracé tel quel : caractère suivant"
            ),
            disabled <-- pad.hasDrawn.map(!_),
            onMouseDown.preventDefault --> (_ => ()),
            onClick --> (_ => pad.takeNow()),
            Tr("Next", "Suivant"),
            ButtonIcon(ButtonIcon.Shape.NextCharacter).node
          )
        )
      ),
      ScriptModes(
        mode,
        available,
        ScriptModes.Layout.Column,
        () => pad.clear()
      ).node
    )
  )
}
