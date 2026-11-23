package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.shared.writing.CharacterStrokes

import com.raquo.laminar.api.L.*

/** A character drawn stroke by stroke, each numbered as it is drawn, over its
  * faint model; it can be replayed.
  */
class StrokeDemonstration(character: CharacterStrokes) extends ReactiveElement {
  private val secondsPerStroke = 1.0
  private val size = CharacterStrokes.boxSize
  private val replays = EventBus[Unit]()

  private def delay(stroke: Int): String =
    s"animation-delay: ${stroke * secondsPerStroke}s"

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

  // Drawn anew at each replay, so that its animations start again.
  private def drawing: SvgElement = svg.svg(
    svg.viewBox := s"0 0 $size $size",
    svg.cls := "demonstration-box",
    guide,
    svg.g(
      svg.cls := "model",
      character.strokes.map(stroke => svg.path(svg.d := stroke.path))
    ),
    svg.g(
      svg.cls := "drawn",
      character.strokes.zipWithIndex.map((stroke, index) =>
        svg.path(
          svg.d := stroke.path,
          svg.pathLength := "1",
          svg.style := delay(index)
        )
      )
    ),
    svg.g(
      svg.cls := "numbers",
      character.strokes.zipWithIndex.map((stroke, index) =>
        svg.text(
          svg.x := s"${stroke.label.x}",
          svg.y := s"${stroke.label.y}",
          svg.style := delay(index),
          s"${index + 1}"
        )
      )
    )
  )

  override lazy val node: HtmlElement = div(
    cls := "demonstration",
    child <-- replays.events.startWith(()).map(_ => drawing),
    button(
      cls := "replay",
      Tr("Replay", "Rejouer"),
      onClick.mapTo(()) --> replays.writer
    )
  )
}
