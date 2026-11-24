package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.shared.kana.Script
import hanamaru.shared.writing.*

import scala.scalajs.js.timers.*

import com.raquo.laminar.api.L.*

/** Kana written one after the other with the stylus, in one box. Once the pen
  * rests a moment, what is drawn is recognised among the kana of `script` (as
  * it stands then) and taken as a character, out of `written`, and the box is
  * cleared for the next one; a drawing far from any kana is left in the box, to
  * be finished, erased or taken all the same. `strokes` are the kana's.
  */
class KanaPad(strokes: List[CharacterStrokes], script: () => Script)
    extends ReactiveElement {
  import KanaPad.*

  private val size = CharacterStrokes.boxSize
  private val recognisers = Script.values.toList
    .map(each => each -> CharacterRecognizer.ofKana(strokes, each))
    .toMap

  private val drawn = Var(List.empty[List[DrawnPoint]])
  private val drawing = Var(Option.empty[List[DrawnPoint]])
  private val unrecognised = Var(false)
  private val taken = EventBus[Written]()
  private var resting = Option.empty[SetTimeoutHandle]

  val written: EventStream[Written] = taken.events

  /** The box emptied, what was drawn in it dropped. */
  def clear(): Unit = {
    stopWaiting()
    drawn.set(Nil)
    unrecognised.set(false)
  }

  private def stopWaiting(): Unit = {
    resting.foreach(clearTimeout)
    resting = None
  }

  // a stroke shorter than this is a slip of the pen, not a stroke
  private val shortestStroke = 2.0

  private def strokeEnded(points: List[DrawnPoint]): Unit =
    if (StrokeMatcher.length(points.map(_.point)) >= shortestStroke) {
      drawn.update(_ :+ points)
      stopWaiting()
      resting = Some(setTimeout(restDelay)(take(anyway = false)))
    }

  private def take(anyway: Boolean): Unit = {
    stopWaiting()
    val strokesDrawn = drawn.now()
    val ranked = recognisers(script()).ranked(strokesDrawn.map(_.map(_.point)))
    ranked.headOption match {
      case Some(best)
          if anyway || best.distance <= CharacterRecognizer.recognisedWithin =>
        drawn.set(Nil)
        unrecognised.set(false)
        taken.writer.onNext(
          Written(strokesDrawn, ranked.take(candidates).map(_.character))
        )
      case _ => unrecognised.set(strokesDrawn.nonEmpty)
    }
  }

  override lazy val node: HtmlElement = div(
    cls := "kana-pad",
    svg.svg(
      svg.viewBox := s"0 0 $size $size",
      svg.cls := "writing-box",
      PenStrokes.guide,
      svg.g(
        svg.cls := "written",
        children <-- drawn.signal.map(_.map(PenStrokes.polyline)),
        child.maybe <-- drawing.signal.map(_.map(PenStrokes.polyline))
      ),
      PenStrokes(
        drawing,
        () => true,
        strokeEnded,
        started = () => {
          stopWaiting()
          unrecognised.set(false)
        }
      )
    ),
    child.maybe <-- unrecognised.signal.map(
      Option.when(_)(
        p(
          cls := "writing-message is-failed",
          Tr(
            "Not recognised as a kana: finish it, erase a stroke, or take it all the same.",
            "Pas reconnu comme un kana : termine-le, efface un trait, ou passe au suivant quand même."
          )
        )
      )
    ),
    div(
      cls := "writing-controls",
      button(
        typ := "button",
        Tr("Erase the last stroke", "Effacer le dernier trait"),
        disabled <-- drawn.signal.map(_.isEmpty),
        onClick --> { _ =>
          stopWaiting()
          unrecognised.set(false)
          drawn.update(_.dropRight(1))
        }
      ),
      button(
        typ := "button",
        Tr("Next character", "Caractère suivant"),
        disabled <-- drawn.signal.map(_.isEmpty),
        onClick --> (_ => take(anyway = true))
      )
    ),
    onUnmountCallback(_ => stopWaiting())
  )
}

object KanaPad {

  /** A character written: its strokes, and the kana it may be, closest first.
    */
  final case class Written(
      strokes: List[List[DrawnPoint]],
      candidates: List[String]
  )

  /** How long the pen rests before what is drawn is taken as a character, in
    * milliseconds: longer than a pause between two strokes of a kana.
    */
  val restDelay: Double = 900

  private val candidates = 4
}
