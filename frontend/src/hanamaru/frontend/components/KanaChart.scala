package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.kana.*
import hanamaru.shared.learner.{ItemStatus, KanaProgress}

import scala.scalajs.js

import com.raquo.laminar.api.L.*

/** Every kana, script by script, laid out as the usual chart, each marked with
  * where the learner stands on it; each leads to how it is written.
  */
class KanaChart(bus: AppBus) extends ReactiveElement {

  // loaded once, then again after each review, the placement test's included
  private val loaded: Signal[Option[AppResult[Map[String, KanaProgress]]]] =
    EventStream
      .merge(
        EventStream.fromValue(()),
        bus.events.events.collect {
          case AppEvent.WritingRecorded(_, Some(_)) => ()
          case AppEvent.PlacementUpdated(_)         => ()
        }
      )
      .flatMapSwitch(_ =>
        bus.request(AppCommand.LoadKanaProgress) {
          case AppEvent.KanaProgressLoaded(progress) =>
            progress.map(each => each.character -> each).toMap
        }
      )
      .map(Some(_))
      .startWith(None)

  private val progress: Signal[Map[String, KanaProgress]] =
    loaded.map(_.flatMap(_.toOption).getOrElse(Map.empty))

  private def status(kana: Kana): Signal[ItemStatus] =
    progress.map(_.get(kana.character).fold(ItemStatus.Unknown)(_.status))

  private def due(kana: Kana): Signal[Boolean] =
    progress.map(
      _.get(kana.character).flatMap(_.due).exists(_ <= js.Date.now())
    )

  private def progressLegend: HtmlElement = div(
    cls := "kana-legend",
    span(cls := "mark is-learning", Tr("being learned", "en apprentissage")),
    span(cls := "mark is-to-confirm", Tr("to confirm", "à confirmer")),
    span(cls := "mark is-known", Tr("known", "acquis")),
    span(cls := "mark is-due", Tr("to review", "à revoir"))
  )

  private def scriptName(script: Script): String = script match {
    case Script.Hiragana => Tr("Hiragana", "Hiragana")
    case Script.Katakana => Tr("Katakana", "Katakana")
  }

  private def groupName(group: KanaGroup): String = group match {
    case KanaGroup.Basic  => Tr("Basic sounds", "Sons de base")
    case KanaGroup.Voiced =>
      Tr(
        "Voiced sounds (dakuten, handakuten)",
        "Sons voisés (dakuten, handakuten)"
      )
    case KanaGroup.Small => Tr("Small kana", "Petits kana")
  }

  private def cell(kana: Option[Kana]): HtmlElement = kana match {
    case Some(kana) =>
      PageLink(
        bus,
        Page.Character(kana),
        cls := "kana-cell",
        cls("is-learning") <-- status(kana).map(_ == ItemStatus.Learning),
        cls("is-to-confirm") <-- status(kana).map(_ == ItemStatus.ToConfirm),
        cls("is-known") <-- status(kana).map(_ == ItemStatus.Known),
        cls("is-due") <-- due(kana),
        span(cls := "kana", lang := "ja", kana.character),
        span(cls := "romaji", kana.romaji)
      ).node
    case None => div(cls := "kana-cell is-gap")
  }

  private def scriptChart(script: Script): HtmlElement = sectionTag(
    cls := "kana-script",
    h2(scriptName(script)),
    Kana
      .chart(script)
      .map((group, rows) =>
        div(
          cls := "kana-group",
          h3(groupName(group)),
          div(cls := "kana-grid", rows.flatten.map(cell))
        )
      )
  )

  // Until the placement test is over, the chart invites to it: what is known
  // already is then confirmed quickly instead of learned again.
  private val placementInvitation: EventStream[Option[HtmlElement]] =
    bus
      .request(AppCommand.LoadPlacement) {
        case AppEvent.PlacementUpdated(state) =>
          state
      }
      .map(_.toOption.filter(!_.finished).map { state =>
        div(
          cls := "invitation",
          p(
            if (state.started) {
              Tr(
                "The kana placement test is under way.",
                "Le test de placement des kana est en cours."
              )
            } else {
              Tr(
                "Start with the placement test: what you already know will be confirmed quickly instead of learned again.",
                "Commence par le test de placement : ce que tu sais déjà sera confirmé rapidement au lieu d'être réappris."
              )
            }
          ),
          PageLink(
            bus,
            Page.Placement,
            cls := "button primary",
            if (state.started) Tr("Resume the test", "Reprendre le test")
            else Tr("Take the test", "Passer le test")
          )
        )
      })

  override lazy val node: HtmlElement = div(
    cls := "kana-chart",
    child.maybe <-- placementInvitation,
    div(
      cls := "chart-bar",
      progressLegend,
      div(
        cls := "chart-actions",
        PageLink(bus, Page.Review, cls := "button", Tr("Review", "Réviser")),
        PageLink(
          bus,
          Page.Session,
          cls := "button primary",
          Tr("Express session", "Séance express")
        )
      )
    ),
    child.maybe <-- loaded.map(
      _.flatMap(_.left.toOption).map(reason =>
        p(
          cls := "is-failed",
          Tr(
            s"Where you stand could not be loaded: $reason",
            s"Ta progression n'a pas pu être chargée : $reason"
          )
        )
      )
    ),
    Script.values.toList.map(scriptChart)
  )
}
