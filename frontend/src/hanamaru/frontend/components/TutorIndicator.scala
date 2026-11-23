package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.ModelState

import com.raquo.laminar.api.L.*

/** Whether the tutor's model is ready, in a word, beside the title. */
class TutorIndicator(bus: AppBus) extends ReactiveElement {

  private def word(state: ModelState): (String, String) = state match {
    case ModelState.Ready    => "is-ready" -> Tr("Tutor ready", "Tuteur prêt")
    case ModelState.Starting =>
      "is-starting" -> Tr("Tutor loading…", "Tuteur en chargement…")
    case _ => "is-off" -> Tr("No tutor", "Pas de tuteur")
  }

  override lazy val node: HtmlElement = span(
    cls := "tutor-indicator",
    children <-- bus.events.events
      .collect { case AppEvent.ServerChecked(status) => word(status.model) }
      .map((state, text) => List(span(cls := state, text)))
  )
}
