package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.ReferenceDataState
import hanamaru.shared.kana.{Kana, Script}

import com.raquo.laminar.api.L.*

/** How one kana is written: the character in the textbook style, its reading,
  * and its strokes drawn in order.
  */
class CharacterPage(bus: AppBus) extends ReactiveElement {

  private val shown: Signal[Option[Kana]] = bus.events.events
    .collect {
      case AppEvent.PageShown(Page.Character(kana)) => Some(kana)
      case AppEvent.PageShown(_)                    => None
    }
    .startWith(None)

  private val ready: Signal[Boolean] = bus.events.events
    .collect { case AppEvent.ServerChecked(status) =>
      status.referenceData == ReferenceDataState.Ready
    }
    .startWith(false)

  private val demonstration: EventStream[Option[HtmlElement]] =
    shown.combineWith(ready).flatMapSwitch {
      case (Some(kana), true) =>
        bus
          .request(AppCommand.LoadStrokes(kana.character)) {
            case AppEvent.StrokesLoaded(strokes)
                if strokes.character == kana.character =>
              strokes
          }
          .map {
            case Right(strokes) => Some(StrokeDemonstration(strokes).node)
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
      case _ => EventStream.fromValue(None)
    }

  private def scriptName(script: Script): String = script match {
    case Script.Hiragana => Tr("hiragana", "hiragana")
    case Script.Katakana => Tr("katakana", "katakana")
  }

  private def heading(kana: Kana): HtmlElement = div(
    cls := "character-heading",
    span(cls := "character-model", lang := "ja", kana.character),
    div(
      span(cls := "character-reading", kana.romaji),
      span(cls := "character-script", scriptName(kana.script))
    )
  )

  override lazy val node: HtmlElement = div(
    cls := "character-page",
    PageLink(
      bus,
      Page.Chart,
      cls := "back",
      Tr("← Kana chart", "← Tableau des kana")
    ),
    child.maybe <-- shown.map(_.map(heading)),
    child.maybe <-- demonstration
  )
}
