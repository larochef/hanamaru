package hanamaru.frontend.components

import hanamaru.frontend.components.KanaInput.Mode
import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.AppBus
import hanamaru.shared.kana.{Kana, Script}
import hanamaru.shared.writing.*

import com.raquo.laminar.api.L.*

/** A word written from memory with the stylus, one character after the other,
  * each as the learner thinks it goes, with no correction in between: a kana is
  * taken once the pen rests, if it looks like one, a kanji once the pen rests
  * with all its strokes drawn, and the box is cleared for the next one. The
  * learner chooses what they write it in (`forms`: the word as written in each
  * script offered), in the box's frame ([[WritingBox]]): in kana, the script is
  * theirs to know, each character recognised in the one chosen; in kanji, its
  * kana as they are. The answer keeps the characters as drawn, one place for
  * each of the word's: the drawing is what is corrected, so its dakuten and its
  * small kana are drawn too. The characters come out of `written` with the word
  * they were written for, to be corrected together: a kana whose box looks like
  * others rather than itself (not among the closest three) is another
  * character, which makes it another word. With `stylus`, the learner may turn
  * to the keyboard instead.
  */
class HandwrittenWord(
    bus: AppBus,
    forms: Map[Mode, String],
    starts: Mode,
    dontKnow: () => Unit,
    stylus: Option[Var[Boolean]] = None
) extends ReactiveElement {
  private val mode = Var(starts)
  private val done = Var(List.empty[KanaPad.Written])
  private val ended = Var(false)
  private val finished = EventBus[(String, List[WritingAttempt])]()

  /** The word the characters were written for, and the characters. */
  val written: EventStream[(String, List[WritingAttempt])] = finished.events

  private def word: String = forms.getOrElse(mode.now(), "")

  private def charactersOf(word: String): List[String] =
    word.toList.map(_.toString)

  // what the character to write is recognised among: in kana, the script
  // chosen; in kanji, a kanji as itself, a kana in its script
  private def askedAt(index: Int): KanaPad.Asked =
    mode.now() match {
      case Mode.Hiragana => KanaPad.Asked.KanaOf(Script.Hiragana)
      case Mode.Katakana => KanaPad.Asked.KanaOf(Script.Katakana)
      case Mode.Kanji    =>
        charactersOf(word).lift(index) match {
          case Some(character)
              if Kana.find(character).isEmpty && character != "ー" =>
            KanaPad.Asked.Kanji(List(character))
          case character =>
            KanaPad.Asked.KanaOf(
              character.flatMap(Kana.find).fold(Script.Katakana)(_.script)
            )
        }
    }

  private def isKanji(character: String): Boolean =
    Kana.find(character).isEmpty && character != "ー"

  // the kana drawn at `index` looks like, when not like its own
  private def looksLike(index: Int, drawn: KanaPad.Written): List[String] =
    askedAt(index) match {
      case KanaPad.Asked.KanaOf(_)
          if drawn.candidates.nonEmpty &&
            !drawn.candidates
              .take(HandwrittenWord.closest)
              .contains(charactersOf(word)(index)) =>
        drawn.candidates.take(HandwrittenWord.closest)
      case _ => Nil
    }

  private val size = CharacterStrokes.boxSize

  // a character written, as small as a letter
  private def ink(strokes: List[List[DrawnPoint]]): SvgElement = svg.svg(
    svg.viewBox := s"0 0 $size $size",
    svg.cls := "character-ink",
    svg.g(svg.cls := "written", strokes.map(PenStrokes.polyline))
  )

  private def places: Signal[List[HtmlElement]] =
    mode.signal.combineWith(done.signal).map { (now, written) =>
      charactersOf(forms.getOrElse(now, "")).zipWithIndex.map {
        (character, index) =>
          span(
            cls := "character-place",
            cls("is-kanji") := now == Mode.Kanji && isKanji(character),
            cls("is-done") := written.size > index,
            cls("is-current") := written.size == index,
            written.lift(index).map(drawn => ink(drawn.strokes))
          )
      }
    }

  private def writing(strokes: List[CharacterStrokes]): HtmlElement = {
    val models = strokes
      .map(character =>
        character.character -> character.strokes
          .map(stroke => SvgPath.points(stroke.path))
      )
      .toMap
    val missing = forms.values
      .flatMap(charactersOf)
      .toList
      .distinct
      .filterNot(models.contains)
    if (missing.nonEmpty) {
      p(
        cls := "is-failed",
        Tr(
          s"No strokes for ${missing.mkString}",
          s"Pas de traits pour ${missing.mkString}"
        )
      )
    } else {
      val pad = KanaPad(strokes, () => askedAt(done.now().size))
      def finish(): Unit = {
        val asked = word
        ended.set(true)
        finished.writer.onNext(
          asked -> charactersOf(asked)
            .zip(done.now())
            .zipWithIndex
            .map { case ((character, drawn), index) =>
              WritingCorrection
                .attempt(character, drawn.strokes, models(character))
                .copy(looksLike = looksLike(index, drawn))
            }
        )
      }
      val complete = done.signal
        .combineWith(mode.signal, ended.signal)
        .map((written, now, isEnded) =>
          !isEnded && written.size == forms.getOrElse(now, "").length
        )
      div(
        cls := "handwritten-word",
        // another script, another word: written again from its start
        mode.signal.changes --> { _ =>
          done.set(Nil)
          pad.clear()
        },
        pad.written --> { character =>
          if (!ended.now() && done.now().size < word.length) {
            done.update(_ :+ character)
          }
        },
        div(
          cls := "answer-panel",
          div(
            cls := "answer-head",
            p(cls := "frame-label", Tr("Your answer", "Ta réponse")),
            stylus.map(choice => InputChoice(choice).node)
          ),
          div(
            cls := "answer-frame",
            div(cls := "answer-content character-places", children <-- places),
            div(
              cls := "answer-tools",
              ToolButton(
                ToolButton.Face.Icon(ButtonIcon.Shape.Backspace),
                Tr(
                  "Take away the last character",
                  "Effacer le dernier caractère"
                ),
                () => {
                  pad.clear()
                  done.update(_.dropRight(1))
                },
                done.signal
                  .combineWith(ended.signal)
                  .map((written, isEnded) => written.nonEmpty && !isEnded)
              ).node
            )
          ),
          p(
            cls := "answer-hint",
            Tr(
              "Dakuten, handakuten and small kana are drawn here: they are part of what is corrected.",
              "Tenten, maru et petits kana se tracent ici : ils font partie de ce qui est corrigé."
            )
          ),
          WritingBox(pad, mode, forms.keySet).node
        ),
        AnswerActions(
          dontKnow,
          submit = Some(() => finish()),
          ready = complete
        ).node
      )
    }
  }

  override lazy val node: HtmlElement =
    ManyStrokesOf(
      bus,
      CharacterRecognizer.kana + forms.values.mkString,
      writing
    ).node
}

object HandwrittenWord {

  /** How many of the closest kana a box's own must be among: the recogniser
    * puts a kana written right first nineteen times out of twenty, among the
    * first three nearly always (`backend.calibration`).
    */
  val closest = 3
}
