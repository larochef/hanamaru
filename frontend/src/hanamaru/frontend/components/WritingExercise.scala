package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.writing.*

import scala.scalajs.js

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** A character written with the stylus, stroke by stroke, each judged as it
  * ends. Guided, over its faint model: a wrong stroke is said, erased, and
  * drawn again. From memory, in an empty box: a wrong stroke ends the attempt,
  * and the expected one is shown.
  */
class WritingExercise(
    bus: AppBus,
    character: CharacterStrokes,
    step: WritingStep
) extends ReactiveElement {
  private val size = CharacterStrokes.boxSize
  private val guided = step == WritingStep.Guided
  private val model =
    character.strokes.map(stroke => SvgPath.points(stroke.path))

  // a stroke shorter than this is a slip of the pen, not a stroke
  private val shortestStroke = 2.0

  private val accepted = Var(List.empty[List[DrawnPoint]])
  private val drawing = Var(Option.empty[List[DrawnPoint]])
  private val judged = Var(List.empty[JudgedStroke])

  /** The last wrong stroke: which stroke was expected, and why it was refused.
    */
  private val refused = Var(Option.empty[(Int, StrokeVerdict)])
  private val ended = Var(false)
  private val attempts = EventBus[WritingAttempt]()
  private var strokeStart = 0.0

  private def drawnPoint(
      box: dom.Element,
      event: dom.PointerEvent
  ): DrawnPoint = {
    val bounds = box.getBoundingClientRect()
    DrawnPoint(
      ((event.clientX - bounds.left) / bounds.width * size).max(0).min(size),
      ((event.clientY - bounds.top) / bounds.height * size).max(0).min(size),
      event.pressure,
      event.timeStamp - strokeStart
    )
  }

  // A fast pen moves several times between two events: the browser keeps
  // those positions as the event's coalesced ones.
  private def movesOf(event: dom.PointerEvent): List[dom.PointerEvent] = {
    val dynamic = event.asInstanceOf[js.Dynamic]
    if (js.typeOf(dynamic.getCoalescedEvents) == "function") {
      val coalesced =
        dynamic.getCoalescedEvents().asInstanceOf[js.Array[dom.PointerEvent]]
      if (coalesced.isEmpty) List(event) else coalesced.toList
    } else {
      List(event)
    }
  }

  // The palm and fingers rest on the screen: only the pen (or a mouse) draws.
  private def draws(event: dom.PointerEvent): Boolean =
    event.pointerType != "touch" && !ended.now()

  private def strokeEnded(points: List[DrawnPoint]): Unit = {
    val line = points.map(_.point)
    if (StrokeMatcher.length(line) >= shortestStroke) {
      val expected = accepted.now().size
      val judgement = StrokeMatcher.judge(line, model, expected)
      judged.update(_ :+ JudgedStroke(points, expected, judgement))
      if (judgement.verdict == StrokeVerdict.Accepted) {
        accepted.update(_ :+ points)
        refused.set(None)
        if (accepted.now().size == model.size) {
          end(completed = true)
        }
      } else {
        refused.set(Some(expected -> judgement.verdict))
        if (!guided) {
          end(completed = false)
        }
      }
    }
  }

  private def end(completed: Boolean): Unit = {
    ended.set(true)
    record(completed)
  }

  private def record(completed: Boolean): Unit =
    attempts.writer.onNext(
      WritingAttempt(character.character, step, judged.now(), completed)
    )

  private def restart(): Unit = {
    if (!ended.now() && judged.now().nonEmpty) {
      record(completed = false)
    }
    accepted.set(Nil)
    drawing.set(None)
    judged.set(Nil)
    refused.set(None)
    ended.set(false)
  }

  private def polyline(points: List[DrawnPoint]): SvgElement =
    svg.polyline(
      svg.points := points.map(point => s"${point.x},${point.y}").mkString(" ")
    )

  // the dashed cross of practice paper, which helps place the strokes
  private def guide: SvgElement = svg.g(
    svg.cls := "guide",
    svg.line(
      svg.x1 := s"${size / 2.0}",
      svg.y1 := "0",
      svg.x2 := s"${size / 2.0}",
      svg.y2 := s"$size"
    ),
    svg.line(
      svg.x1 := "0",
      svg.y1 := s"${size / 2.0}",
      svg.x2 := s"$size",
      svg.y2 := s"${size / 2.0}"
    )
  )

  // the stroke that was expected, drawn from its start, so its direction shows
  private def expectedStroke(index: Int): SvgElement = svg.g(
    svg.cls := "expected",
    svg.path(svg.d := character.strokes(index).path, svg.pathLength := "1")
  )

  private def box: SvgElement = svg.svg(
    svg.viewBox := s"0 0 $size $size",
    svg.cls := "writing-box",
    guide,
    Option.when(guided)(
      svg.g(
        svg.cls := "model",
        character.strokes.map(stroke => svg.path(svg.d := stroke.path))
      )
    ),
    svg.g(
      svg.cls := "written",
      children <-- accepted.signal.map(_.map(polyline)),
      child.maybe <-- drawing.signal.map(_.map(polyline))
    ),
    child.maybe <-- refused.signal.map(
      _.map((index, _) => expectedStroke(index))
    ),
    inContext { element =>
      Seq(
        onPointerDown.filter(draws) --> { event =>
          element.ref.setPointerCapture(event.pointerId)
          strokeStart = event.timeStamp
          drawing.set(Some(List(drawnPoint(element.ref, event))))
        },
        onPointerMove.filter(draws) --> { event =>
          drawing.update(
            _.map(
              _ ++ movesOf(event).map(move => drawnPoint(element.ref, move))
            )
          )
        },
        onPointerUp.filter(draws) --> { _ =>
          drawing.now().foreach(strokeEnded)
          drawing.set(None)
        },
        onPointerCancel --> { _ => drawing.set(None) }
      )
    }
  )

  private def refusal(index: Int, verdict: StrokeVerdict): String = {
    val number = index + 1
    verdict match {
      case StrokeVerdict.WrongDirection =>
        Tr(
          s"Stroke $number goes the other way.",
          s"Le trait $number va dans l'autre sens."
        )
      case StrokeVerdict.WrongOrder(drawn) =>
        Tr(
          s"That is stroke $drawn: stroke $number comes first.",
          s"C'est le trait $drawn : le trait $number vient avant."
        )
      case StrokeVerdict.Misdrawn | StrokeVerdict.Accepted =>
        Tr(
          s"That is not stroke $number.",
          s"Ce n'est pas le trait $number."
        )
    }
  }

  private val message: Signal[Option[HtmlElement]] =
    refused.signal.combineWith(ended.signal).map {
      case (_, true) if accepted.now().size == model.size =>
        Some(
          p(
            cls := "writing-message is-done",
            Tr("Well written!", "Bien écrit !")
          )
        )
      case (Some((index, verdict)), isEnded) =>
        val next =
          if (isEnded) {
            Tr(" It is shown in red.", " Il est montré en rouge.")
          } else {
            Tr(" Draw it again.", " Recommence-le.")
          }
        Some(
          p(cls := "writing-message is-failed", refusal(index, verdict) + next)
        )
      case _ => None
    }

  private val unsaved: EventStream[Option[HtmlElement]] =
    attempts.events.flatMapSwitch(attempt =>
      bus
        .request(AppCommand.RecordWriting(attempt)) {
          case AppEvent.WritingRecorded(`attempt`) => ()
        }
        .map {
          case Right(())    => None
          case Left(reason) =>
            Some(
              p(
                cls := "is-failed",
                Tr(
                  s"The attempt could not be kept: $reason",
                  s"L'essai n'a pas pu être gardé : $reason"
                )
              )
            )
        }
    )

  override lazy val node: HtmlElement = div(
    cls := "writing-exercise",
    box,
    child.maybe <-- message,
    child.maybe <-- unsaved,
    button(
      cls := "replay",
      Tr("Start again", "Recommencer"),
      onClick --> (_ => restart())
    )
  )
}
