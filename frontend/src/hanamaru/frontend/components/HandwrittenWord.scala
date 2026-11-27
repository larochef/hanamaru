package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.AppBus
import hanamaru.shared.kana.{Kana, Script}
import hanamaru.shared.writing.*

import com.raquo.laminar.api.L.*

/** A word written from memory with the stylus, one character after the other,
  * each as the learner thinks it goes, with no correction in between: a kana is
  * taken once the pen rests, if it looks like one, a kanji once the pen rests
  * with all its strokes drawn, and the box is cleared for the next one; the
  * learner may go back to one, and says when the word is written. The
  * characters come out of `written` then, to be corrected together: a kana
  * whose box looks like others rather than itself (not among the closest three)
  * is another character, which makes it another word.
  */
class HandwrittenWord(bus: AppBus, word: String) extends ReactiveElement {
  private val characters = word.toList.map(_.toString)
  private val done = Var(List.empty[KanaPad.Written])
  private val ended = Var(false)
  private val finished = EventBus[List[WritingAttempt]]()

  val written: EventStream[List[WritingAttempt]] = finished.events

  // what the character to write is recognised among: a kana among its
  // script's, ー among the katakana, a kanji as itself
  private def askedAt(index: Int): KanaPad.Asked =
    characters.lift(index) match {
      case Some(character)
          if Kana.find(character).isEmpty && character != "ー" =>
        KanaPad.Asked.Kanji(List(character))
      case character =>
        KanaPad.Asked.KanaOf(
          character.flatMap(Kana.find).fold(Script.Katakana)(_.script)
        )
    }

  private def isKanjiAt(index: Int): Boolean = askedAt(index) match {
    case KanaPad.Asked.Kanji(_)  => true
    case KanaPad.Asked.KanaOf(_) => false
  }

  // the kana drawn at `index` looks like, when not like its own
  private def looksLike(index: Int, drawn: KanaPad.Written): List[String] =
    askedAt(index) match {
      case KanaPad.Asked.KanaOf(_)
          if drawn.candidates.nonEmpty &&
            !drawn.candidates
              .take(HandwrittenWord.closest)
              .contains(characters(index)) =>
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
      val pad = KanaPad(strokes, () => askedAt(done.now().size))
      div(
        cls := "handwritten-word",
        div(
          cls := "character-places",
          characters.indices.map(index =>
            span(
              cls := "character-place",
              cls("is-kanji") := isKanjiAt(index),
              cls("is-done") <-- done.signal.map(_.size > index),
              cls("is-current") <-- done.signal.map(_.size == index),
              child.maybe <-- done.signal.map(
                _.lift(index).map(written => ink(written.strokes))
              )
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
              done.update(_ :+ character)
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
                  .zipWithIndex
                  .map { case ((character, drawn), index) =>
                    WritingCorrection
                      .attempt(character, drawn.strokes, models(character))
                      .copy(looksLike = looksLike(index, drawn))
                  }
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

object HandwrittenWord {

  /** How many of the closest kana a box's own must be among: the recogniser
    * puts a kana written right first nineteen times out of twenty, among the
    * first three nearly always (`backend.calibration`).
    */
  val closest = 3
}
