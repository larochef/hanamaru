package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.writing.WritingStep

import com.raquo.laminar.api.L.*

/** A new character shown as it is written, stroke by stroke, and practised
  * freely as the learner likes: its strokes again, written over its model, or
  * from memory, as often as wanted. Practice only: nothing of it is kept as a
  * review, the lesson's own steps follow.
  */
class WritingPractice(bus: AppBus, character: String) extends ReactiveElement {

  private val step = Var(WritingStep.Demonstration)

  private def tabs: HtmlElement = div(
    cls := "step-tabs",
    WritingStep.values.toList.map(each =>
      button(
        cls("is-selected") <-- step.signal.map(_ == each),
        WritingPractice.stepName(each),
        onClick.mapTo(each) --> step.writer
      )
    )
  )

  override lazy val node: HtmlElement = div(
    cls := "writing-practice",
    tabs,
    child <-- step.signal.map(shown =>
      StrokesOf(
        bus,
        character,
        strokes =>
          shown match {
            case WritingStep.Demonstration => StrokeDemonstration(strokes).node
            case _                         =>
              WritingExercise(strokes, shown, canStartAgain = true).node
          }
      )
    )
  )
}

object WritingPractice {

  /** A writing step, as its tab names it. */
  def stepName(step: WritingStep): String = step match {
    case WritingStep.Demonstration => Tr("The strokes", "Les traits")
    case WritingStep.Guided        => Tr("Guided", "Guidé")
    case WritingStep.FromMemory    => Tr("From memory", "De mémoire")
  }
}
