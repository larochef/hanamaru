package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.writing.WritingStep

import com.raquo.laminar.api.L.*

/** A character the learner did not remember: shown again stroke by stroke, then
  * written over its model, as practice; `goOn` once written. Its card, missed,
  * comes back soon to be written from memory again.
  */
class Relearn(bus: AppBus, character: String, goOn: () => Unit)
    extends ReactiveElement {

  override lazy val node: HtmlElement = div(
    cls := "relearn",
    p(
      cls := "placement-prompt",
      Tr("Here it is, stroke by stroke:", "Le voici, trait par trait :")
    ),
    StrokesOf(bus, character, StrokeDemonstration(_).node),
    p(
      cls := "placement-prompt",
      Tr(
        "Write it again over its model; it will come back soon from memory.",
        "Réécris-le sur son modèle ; il reviendra bientôt de mémoire."
      )
    ),
    StrokesOf(
      bus,
      character,
      strokes => {
        val writing =
          WritingExercise(strokes, WritingStep.Guided, canStartAgain = true)
        writing.node.amend(
          child.maybe <-- writing.attempts
            .filter(_.completed)
            .mapTo(
              Some(
                button(
                  cls := "primary",
                  Tr("Continue", "Continuer"),
                  onMountFocus,
                  onClick --> (_ => goOn())
                )
              )
            )
        )
      }
    )
  )
}
