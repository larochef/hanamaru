package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.kana.*
import hanamaru.shared.writing.{CharacterRecognizer, PadCharacter}

import com.raquo.laminar.api.L.*

/** The stylus beside kana fields, as the learner chooses (`stylus`): kana
  * written in a pad, each put at the end of the field it writes in now
  * (`field`: a sentence's one, or the blank chosen among several) once
  * recognised, in the script the field is in, with the kana the last one may be
  * instead and buttons to change it (small, dakuten, maru) or erase it; or, the
  * field switched to them (漢), among the kanji the learner writes. `keep` keeps
  * the drawings of an answer, to tune the recognition.
  */
class StylusField(
    bus: AppBus,
    target: () => KanaInput,
    stylus: Var[Boolean]
) extends ReactiveElement {

  private def field = target()

  // the kana written in the pad, in the order they are in the field
  private val padded = Var(List.empty[KanaPad.Written])

  /** The drawings of `answer` kept, if it was all written in the pad. */
  def keep(answer: String): Unit = keptForTuning(answer, padded.now())

  // Kana written with the stylus, each put at the end of the field once
  // recognised, in the script the field is in; the kana the last one may be
  // instead, to choose from.
  // The kana of an answer written in the pad, each with the kana it is in the
  // answer, as the learner left it: only when the whole answer was written in
  // the pad, so that each drawing is known to be the kana in its place.
  private def keptForTuning(
      answer: String,
      padded: List[KanaPad.Written]
  ): Unit =
    if (padded.nonEmpty && answer.length == padded.size) {
      bus.send(
        AppCommand.RecordPadCharacters(
          answer.toList
            .map(_.toString)
            .zip(padded)
            .map((character, written) =>
              PadCharacter(
                if (Kana.isSmall(character)) Kana.smallOrLarge(character)
                else character,
                written.strokes,
                written.candidates
              )
            )
        )
      )
    }

  private def pad: HtmlElement = {
    val others = Var(List.empty[String])
    // a button that changes the last kana: made small, a dakuten added…
    def changeOfLast(
        sign: String,
        meaning: String,
        changed: String => String
    ) = button(
      typ := "button",
      lang := "ja",
      cls := "kana-change",
      title := meaning,
      sign,
      onClick --> { _ =>
        field.last.foreach(last => field.replaceLast(changed(last)))
        others.set(Nil)
      }
    )
    ManyStrokesOf(
      bus,
      CharacterRecognizer.kana + field.kanji.mkString,
      strokes => {
        // a small kana is made with its button: recognised by its size
        // alone, it was often taken for its large one, and the other way
        val pad = KanaPad(
          strokes.filterNot(character => Kana.isSmall(character.character)),
          () =>
            field.modeNow match {
              case KanaInput.Mode.Kanji    => KanaPad.Asked.Kanji(field.kanji)
              case KanaInput.Mode.Katakana =>
                KanaPad.Asked.KanaOf(Script.Katakana)
              case KanaInput.Mode.Hiragana =>
                KanaPad.Asked.KanaOf(Script.Hiragana)
            }
        )
        div(
          cls := "sentence-pad",
          pad.node.amend(pad.written --> { character =>
            padded.update(_ :+ character)
            character.candidates.headOption.foreach(field.append)
            others.set(character.candidates.drop(1))
          }),
          div(
            cls := "pad-candidates",
            child.maybe <-- others.signal.map(candidates =>
              Option.when(candidates.nonEmpty)(
                span(Tr("Or else: ", "Ou bien : "))
              )
            ),
            children <-- others.signal.map(
              _.map(candidate =>
                button(
                  typ := "button",
                  lang := "ja",
                  candidate,
                  onClick --> { _ =>
                    val written = field.value.takeRight(1)
                    field.replaceLast(candidate)
                    others.update(_.filterNot(_ == candidate) :+ written)
                  }
                )
              )
            ),
            changeOfLast(
              "小",
              Tr(
                "The last kana small, or back to its usual size (ゃ, っ)",
                "Le dernier kana en petit, ou revenu à sa taille (ゃ, っ)"
              ),
              Kana.smallOrLarge
            ),
            changeOfLast(
              "゛",
              Tr(
                "A dakuten on the last kana, or taken away (が)",
                "Un tenten sur le dernier kana, ou retiré (が)"
              ),
              Kana.withDakuten
            ),
            changeOfLast(
              "゜",
              Tr(
                "A handakuten on the last kana, or taken away (ぱ)",
                "Un maru sur le dernier kana, ou retiré (ぱ)"
              ),
              Kana.withHandakuten
            ),
            button(
              typ := "button",
              Tr("Erase the last character", "Effacer le dernier caractère"),
              onClick --> { _ =>
                field.deleteLast()
                padded.update(_.dropRight(1))
                others.set(Nil)
              }
            )
          )
        )
      }
    ).node
  }

  override lazy val node: HtmlElement = div(
    label(
      cls := "stylus-choice",
      input(
        typ := "checkbox",
        checked <-- stylus.signal,
        onInput.mapToChecked --> stylus.writer
      ),
      Tr("Write with the stylus", "Écrire au stylet")
    ),
    child <-- stylus.signal.map(on => if (on) pad else emptyNode)
  )
}

object StylusField {

  /** The stylus writing in one field. */
  def apply(bus: AppBus, field: KanaInput, stylus: Var[Boolean]): StylusField =
    new StylusField(bus, () => field, stylus)
}
