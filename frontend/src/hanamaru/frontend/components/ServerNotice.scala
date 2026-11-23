package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.*

import com.raquo.laminar.api.L.*

/** What keeps the server from serving fully: its reference data being
  * downloaded on first start or failed, its tutor's model not set up, missing
  * or failed. Nothing once all is well.
  */
class ServerNotice(bus: AppBus) extends ReactiveElement {

  private def referenceData(
      name: String,
      state: ReferenceDataState
  ): Option[HtmlElement] =
    state match {
      case ReferenceDataState.Ready     => None
      case ReferenceDataState.Preparing =>
        Some(
          p(
            Tr(
              s"Preparing the reference data ($name), once only…",
              s"Préparation des données de référence ($name), une seule fois…"
            )
          )
        )
      case ReferenceDataState.Failed(reason) =>
        Some(
          p(
            cls := "is-failed",
            Tr(
              s"The reference data ($name) could not be prepared: $reason",
              s"Les données de référence ($name) n'ont pas pu être préparées : $reason"
            )
          )
        )
    }

  private def model(state: ModelState): Option[HtmlElement] = state match {
    case ModelState.Starting | ModelState.Ready  => None
    case ModelState.NotConfigured(setting, file) =>
      Some(
        p(
          Tr(
            "The tutor is not set up on this machine: give the path to llama-server as ",
            "Le tuteur n'est pas installé sur cette machine : indique le chemin de llama-server à la clé "
          ),
          code(setting),
          Tr(" in ", " dans "),
          code(file),
          Tr(", then start Hanamaru again.", ", puis relance Hanamaru.")
        )
      )
    case ModelState.ModelMissing(name) =>
      Some(
        p(
          Tr(
            "The tutor's model is not in the Hugging Face cache: ",
            "Le modèle du tuteur n'est pas dans le cache Hugging Face : "
          ),
          code(name),
          "."
        )
      )
    case ModelState.Failed(reason) =>
      Some(
        p(
          cls := "is-failed",
          Tr(
            s"The tutor could not be started: $reason",
            s"Le tuteur n'a pas pu démarrer : $reason"
          )
        )
      )
  }

  private def notices(status: ServerStatus): List[HtmlElement] =
    referenceData("KanjiVG", status.strokes).toList ++
      referenceData("JMdict", status.dictionary).toList ++
      referenceData("Sudachi", status.analyser).toList ++
      model(status.model).toList

  override lazy val node: HtmlElement = div(
    cls := "notice",
    children <-- bus
      .request(AppCommand.CheckServer) { case AppEvent.ServerChecked(status) =>
        status
      }
      .map {
        case Right(status) => notices(status)
        case Left(reason)  => List(p(cls := "is-failed", reason))
      }
  )
}
