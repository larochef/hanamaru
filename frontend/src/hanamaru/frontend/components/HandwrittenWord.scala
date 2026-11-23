package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.AppBus
import hanamaru.shared.writing.{WritingAttempt, WritingStep}

import com.raquo.laminar.api.L.*

/** A word written from memory with the stylus, one character after the other,
  * each as the learner thinks it goes, with no correction in between: the
  * characters come out of `written` once the last is done, to be corrected
  * together.
  */
class HandwrittenWord(bus: AppBus, word: String) extends ReactiveElement {
  private val characters = word.toList.map(_.toString)
  private val position = Var(0)
  private val done = Var(List.empty[WritingAttempt])
  private val finished = EventBus[List[WritingAttempt]]()

  val written: EventStream[List[WritingAttempt]] = finished.events

  private def last(index: Int): Boolean = index + 1 >= characters.size

  private def box(index: Int): HtmlElement = StrokesOf(
    bus,
    characters(index),
    strokes => {
      val exercise = WritingExercise(
        strokes,
        WritingStep.FromMemory,
        canStartAgain = false,
        showCorrection = false,
        doneLabel = Some(
          if (last(index)) Tr("Done", "Terminé")
          else Tr("Next character", "Caractère suivant")
        )
      )
      // A Var set while an event is handled takes its value once the event is
      // through: the characters written so far are counted here.
      exercise.node.amend(exercise.attempts --> { attempt =>
        val writtenSoFar = done.now() :+ attempt
        done.set(writtenSoFar)
        if (last(index)) finished.writer.onNext(writtenSoFar)
        else position.set(index + 1)
      })
    }
  ).node

  override lazy val node: HtmlElement = div(
    cls := "handwritten-word",
    div(
      cls := "character-places",
      characters.indices.map(index =>
        span(
          cls := "character-place",
          cls("is-done") <-- position.signal.map(_ > index),
          cls("is-current") <-- position.signal.map(_ == index)
        )
      )
    ),
    child <-- position.signal.map(box)
  )
}
