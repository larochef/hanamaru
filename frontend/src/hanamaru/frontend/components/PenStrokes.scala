package hanamaru.frontend.components

import hanamaru.shared.writing.{CharacterStrokes, DrawnPoint}

import scala.scalajs.js

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** Strokes drawn with the pen in a character's box, in its units: the stroke
  * being drawn is kept in `drawing`; each one, once the pen is lifted, goes to
  * `ended`, and `started` is told when a stroke begins. Only the pen, or a
  * mouse, draws, and only while `draws`: the palm and fingers rest on the
  * screen.
  */
object PenStrokes {
  private val size = CharacterStrokes.boxSize

  def apply(
      drawing: Var[Option[List[DrawnPoint]]],
      draws: () => Boolean,
      ended: List[DrawnPoint] => Unit,
      started: () => Unit = () => ()
  ): Modifier[SvgElement] = {
    var strokeStart = 0.0
    def byPen(event: dom.PointerEvent) =
      event.pointerType != "touch" && draws()
    def drawnPoint(box: dom.Element, event: dom.PointerEvent): DrawnPoint = {
      val bounds = box.getBoundingClientRect()
      DrawnPoint(
        ((event.clientX - bounds.left) / bounds.width * size).max(0).min(size),
        ((event.clientY - bounds.top) / bounds.height * size).max(0).min(size),
        event.pressure,
        event.timeStamp - strokeStart
      )
    }
    inContext { element =>
      Seq(
        onPointerDown.filter(byPen) --> { event =>
          element.ref.setPointerCapture(event.pointerId)
          strokeStart = event.timeStamp
          started()
          drawing.set(Some(List(drawnPoint(element.ref, event))))
        },
        onPointerMove.filter(byPen) --> { event =>
          drawing.update(
            _.map(
              _ ++ movesOf(event).map(move => drawnPoint(element.ref, move))
            )
          )
        },
        onPointerUp.filter(byPen) --> { _ =>
          drawing.now().foreach(ended)
          drawing.set(None)
        },
        onPointerCancel --> { _ => drawing.set(None) }
      )
    }
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

  /** A stroke as drawn, as a line in the box. */
  def polyline(points: List[DrawnPoint]): SvgElement =
    svg.polyline(
      svg.points := points.map(point => s"${point.x},${point.y}").mkString(" ")
    )

  /** The dashed cross of practice paper, which helps place the strokes. */
  def guide: SvgElement = svg.g(
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
}
