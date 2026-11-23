package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.writing.CharacterStrokes

import com.raquo.laminar.api.L.*

/** What is drawn from a character's strokes, once they are loaded. */
class StrokesOf(
    bus: AppBus,
    character: String,
    drawn: CharacterStrokes => HtmlElement
) extends ReactiveElement {

  override lazy val node: HtmlElement = div(
    cls := "strokes-of",
    child.maybe <-- bus
      .request(AppCommand.LoadStrokes(character)) {
        case AppEvent.StrokesLoaded(strokes)
            if strokes.character == character =>
          strokes
      }
      .map {
        case Right(strokes) => Some(drawn(strokes))
        case Left(reason)   =>
          Some(
            p(
              cls := "is-failed",
              Tr(
                s"The strokes could not be loaded: $reason",
                s"Les traits n'ont pas pu être chargés : $reason"
              )
            )
          )
      }
  )
}
