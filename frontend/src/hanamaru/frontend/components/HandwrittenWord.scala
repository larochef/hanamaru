package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.AppBus
import hanamaru.shared.kana.{Kana, Script}
import hanamaru.shared.writing.*

import com.raquo.laminar.api.L.*

/** A word written from memory with the stylus, one character after the other,
  * each as the learner thinks it goes, with no correction in between: a
  * character is taken once the pen rests, if it looks like a kana, and the box
  * is cleared for the next one; the learner may go back to one, and says when
  * the word is written. The characters come out of `written` then, to be
  * corrected together.
  */
class HandwrittenWord(bus: AppBus, word: String) extends ReactiveElement {
  private val characters = word.toList.map(_.toString)
  private val done = Var(List.empty[List[List[DrawnPoint]]])
  private val ended = Var(false)
  private val finished = EventBus[List[WritingAttempt]]()

  val written: EventStream[List[WritingAttempt]] = finished.events

  // the script of the character to write: a kana is recognised among its own
  private def scriptAt(index: Int): Script =
    characters
      .lift(index)
      .flatMap(Kana.find)
      .map(_.script)
      .getOrElse(Script.Katakana)

  private val size = CharacterStrokes.boxSize

  // a character written, as small as a letter
  private def ink(strokes: List[List[DrawnPoint]]): SvgElement = svg.svg(
    svg.viewBox := s"0 0 $size $size",
    svg.cls := "character-ink",
    svg.g(svg.cls := "written", strokes.map(PenStrokes.polyline))
  )

  private def writing(strokes: List[CharacterStrokes]): HtmlElement = {
    val models = strokes
      .map(character =>
        character.character -> character.strokes
          .map(stroke => SvgPath.points(stroke.path))
      )
      .toMap
    val missing = characters.filterNot(models.contains)
    if (missing.nonEmpty) {
      p(
        cls := "is-failed",
        Tr(
          s"No strokes for ${missing.mkString}",
          s"Pas de traits pour ${missing.mkString}"
        )
      )
    } else {
      val pad = KanaPad(strokes, () => scriptAt(done.now().size))
      div(
        cls := "handwritten-word",
        div(
          cls := "character-places",
          characters.indices.map(index =>
            span(
              cls := "character-place",
              cls("is-done") <-- done.signal.map(_.size > index),
              cls("is-current") <-- done.signal.map(_.size == index),
              child.maybe <-- done.signal.map(_.lift(index).map(ink))
            )
          )
        ),
        pad.node.amend(
          display <-- done.signal
            .combineWith(ended.signal)
            .map((written, isEnded) =>
              if (written.size < characters.size && !isEnded) "" else "none"
            ),
          pad.written --> { character =>
            if (done.now().size < characters.size) {
              done.update(_ :+ character.strokes)
            }
          }
        ),
        div(
          cls := "writing-controls",
          button(
            typ := "button",
            Tr("Back one character", "Revenir d'un caractère"),
            disabled <-- done.signal
              .combineWith(ended.signal)
              .map((written, isEnded) => written.isEmpty || isEnded),
            onClick --> { _ =>
              pad.clear()
              done.update(_.dropRight(1))
            }
          ),
          button(
            typ := "button",
            cls := "primary",
            Tr("The word is written", "Mot terminé"),
            disabled <-- done.signal
              .combineWith(ended.signal)
              .map((written, isEnded) =>
                written.size < characters.size || isEnded
              ),
            onClick --> { _ =>
              ended.set(true)
              finished.writer.onNext(
                characters
                  .zip(done.now())
                  .map((character, drawn) =>
                    WritingCorrection
                      .attempt(character, drawn, models(character))
                  )
              )
            }
          )
        )
      )
    }
  }

  override lazy val node: HtmlElement =
    ManyStrokesOf(bus, CharacterRecognizer.kana + word, writing).node
}
