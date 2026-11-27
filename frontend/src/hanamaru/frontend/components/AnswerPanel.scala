package hanamaru.frontend.components

import hanamaru.frontend.components.KanaInput.Mode
import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.kana.*
import hanamaru.shared.writing.{CharacterRecognizer, PadCharacter}

import com.raquo.laminar.api.L.*

/** An answer written in kana fields (`answer`: one field, or a sentence's
  * blanks), with the stylus or the keyboard as the learner chooses (`stylus`),
  * in two frames whose buttons act on what is in them, and on nothing else:
  *   - the answer, with what changes its text: the characters selected (else
  *     the last one) made small, given a dakuten or a handakuten, or taken
  *     away; and, under it, the kana the last one written may be instead;
  *   - with the stylus, the box written in ([[WritingBox]]): each character
  *     recognised there, in the script chosen (`mode`, among `available`), is
  *     put at the end of the field written in now (`target`: the sentence's
  *     one, or the blank chosen).
  *
  * With the stylus, the fields never open the screen's keyboard; with the
  * keyboard, the scripts are under the field, kanji left to the stylus. `keep`
  * keeps the drawings of an answer, to tune the recognition.
  */
class AnswerPanel(
    bus: AppBus,
    target: () => KanaInput,
    stylus: Var[Boolean],
    mode: Var[Mode],
    available: Set[Mode],
    answer: HtmlElement
) extends ReactiveElement {

  private def field = target()

  // the kana written in the box, in the order they are in the field
  private val padded = Var(List.empty[KanaPad.Written])
  // the kana the last one written may be instead
  private val others = Var(List.empty[String])

  /** The drawings of `answer` kept, if it was all written in the box. */
  def keep(answer: String): Unit = {
    val written = padded.now()
    if (written.nonEmpty && answer.length == written.size) {
      bus.send(
        AppCommand.RecordPadCharacters(
          answer.toList
            .map(_.toString)
            .zip(written)
            .map((character, drawn) =>
              PadCharacter(
                if (Kana.isSmall(character)) Kana.smallOrLarge(character)
                else character,
                drawn.strokes,
                drawn.candidates
              )
            )
        )
      )
    }
  }

  // the characters selected, else the last one, changed
  private def changed(change: String => String): Unit = {
    // what was drawn no longer matches the answer, character for character
    if (field.hasSelection) padded.set(Nil)
    field.changeSelected(change)
    others.set(Nil)
  }

  private def textTools: HtmlElement = div(
    cls := "answer-tools",
    ToolButton(
      ToolButton.Face.Sign("小"),
      Tr(
        "Small, or back to its usual size (ゃ, っ): the characters selected, else the last one",
        "En petit, ou revenu à sa taille (ゃ, っ) : les caractères sélectionnés, sinon le dernier"
      ),
      () => changed(Kana.smallOrLarge)
    ).node,
    ToolButton(
      ToolButton.Face.Mark("゛"),
      Tr(
        "A dakuten, or taken away (が): the characters selected, else the last one",
        "Un tenten, ou retiré (が) : les caractères sélectionnés, sinon le dernier"
      ),
      () => changed(Kana.withDakuten)
    ).node,
    ToolButton(
      ToolButton.Face.Mark("゜"),
      Tr(
        "A handakuten, or taken away (ぱ): the characters selected, else the last one",
        "Un maru, ou retiré (ぱ) : les caractères sélectionnés, sinon le dernier"
      ),
      () => changed(Kana.withHandakuten)
    ).node,
    span(cls := "tools-separator"),
    ToolButton(
      ToolButton.Face.Icon(ButtonIcon.Shape.Backspace),
      Tr(
        "Take away the characters selected, else the last one",
        "Effacer les caractères sélectionnés, sinon le dernier"
      ),
      () => {
        if (field.hasSelection) padded.set(Nil)
        else padded.update(_.dropRight(1))
        field.deleteSelected()
        others.set(Nil)
      }
    ).node
  )

  private def candidates: HtmlElement = div(
    cls := "pad-candidates",
    child.maybe <-- others.signal.map(candidates =>
      Option.when(candidates.nonEmpty)(
        span(
          Tr(
            "The last character, or else: ",
            "Le dernier caractère, ou bien : "
          )
        )
      )
    ),
    children <-- others.signal.map(
      _.map(candidate =>
        button(
          typ := "button",
          lang := "ja",
          onMouseDown.preventDefault --> (_ => ()),
          candidate,
          onClick --> { _ =>
            val written = field.value.takeRight(1)
            field.replaceLast(candidate)
            others.update(_.filterNot(_ == candidate) :+ written)
          }
        )
      )
    )
  )

  private def box: HtmlElement = ManyStrokesOf(
    bus,
    CharacterRecognizer.kana + field.kanji.mkString,
    strokes => {
      // a small kana is made with its button: recognised by its size alone,
      // it was often taken for its large one, and the other way
      val pad = KanaPad(
        strokes.filterNot(character => Kana.isSmall(character.character)),
        () =>
          mode.now() match {
            case Mode.Kanji    => KanaPad.Asked.Kanji(field.kanji)
            case Mode.Katakana => KanaPad.Asked.KanaOf(Script.Katakana)
            case Mode.Hiragana => KanaPad.Asked.KanaOf(Script.Hiragana)
          }
      )
      div(
        pad.written --> { character =>
          padded.update(_ :+ character)
          character.candidates.headOption.foreach(field.append)
          others.set(character.candidates.drop(1))
        },
        WritingBox(pad, mode, available).node
      )
    }
  ).node

  override lazy val node: HtmlElement = div(
    cls := "answer-panel",
    // kanji are written, never typed
    stylus.signal.changes.filter(!_) --> { _ =>
      if (mode.now() == Mode.Kanji) mode.set(Mode.Hiragana)
    },
    div(
      cls := "answer-head",
      p(cls := "frame-label", Tr("Your answer", "Ta réponse")),
      InputChoice(stylus).node
    ),
    div(
      cls := "answer-frame",
      div(cls := "answer-content", answer),
      child.maybe <-- stylus.signal.map(Option.when(_)(textTools))
    ),
    child <-- stylus.signal.map(on =>
      if (on) div(candidates, box)
      else
        ScriptModes(
          mode,
          available - Mode.Kanji,
          ScriptModes.Layout.Row,
          () => field.focus()
        ).node
    )
  )
}
