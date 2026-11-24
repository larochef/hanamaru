package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.writing.CharacterStrokes

import com.raquo.laminar.api.L.*

/** What is drawn from the strokes of several characters, once they are loaded:
  * those KanjiVG has.
  */
class ManyStrokesOf(
    bus: AppBus,
    characters: String,
    drawn: List[CharacterStrokes] => HtmlElement
) extends ReactiveElement {

  override lazy val node: HtmlElement = div(
    cls := "strokes-of",
    child.maybe <-- bus
      .request(AppCommand.LoadManyStrokes(characters)) {
        case AppEvent.ManyStrokesLoaded(`characters`, strokes) => strokes
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
