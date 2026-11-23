package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.ReferenceDataState

import com.raquo.laminar.api.L.*

/** What keeps the reference data from being used: being downloaded on first
  * start, or failed. Nothing once it is ready.
  */
class ReferenceDataNotice(bus: AppBus) extends ReactiveElement {

  override lazy val node: HtmlElement = div(
    cls := "notice",
    child.maybe <-- bus
      .request(AppCommand.CheckServer) { case AppEvent.ServerChecked(status) =>
        status.referenceData
      }
      .map {
        case Right(ReferenceDataState.Ready)     => None
        case Right(ReferenceDataState.Preparing) =>
          Some(
            p(
              Tr(
                "Downloading the reference data (KanjiVG), once only…",
                "Téléchargement des données de référence (KanjiVG), une seule fois…"
              )
            )
          )
        case Right(ReferenceDataState.Failed(reason)) =>
          Some(
            p(
              cls := "is-failed",
              Tr(
                s"The reference data could not be prepared: $reason",
                s"Les données de référence n'ont pas pu être préparées : $reason"
              )
            )
          )
        case Left(reason) => Some(p(cls := "is-failed", reason))
      }
  )
}
