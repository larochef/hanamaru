package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.kana.Kana
import hanamaru.shared.session.SessionAnswer
import hanamaru.shared.writing.WritingStep

import com.raquo.laminar.api.L.*

/** A kana met for the first time in a session: shown stroke by stroke, then,
  * with the stylus, written over its model and from memory, the first review of
  * its writing; without it, read, the first review of its reading.
  */
class NewKanaLesson(bus: AppBus, session: Long, kana: Kana, stylus: Boolean)
    extends ReactiveElement {

  private enum Stage {
    case Shown, Guided, FromMemory, Read
  }

  private val stage = Var(Stage.Shown)

  private def answer(sessionAnswer: SessionAnswer): Unit =
    bus.send(AppCommand.AnswerSession(session, sessionAnswer))

  private def continueTo(next: Stage): HtmlElement = button(
    cls := "primary",
    Tr("Continue", "Continuer"),
    onMountFocus,
    onClick.mapTo(next) --> stage.writer
  )

  private def heading: HtmlElement = div(
    cls := "character-heading",
    span(cls := "character-model", lang := "ja", kana.character),
    div(
      span(cls := "character-reading", Kana.shownReading(kana)),
      span(cls := "character-script", Tr("new kana", "nouveau kana"))
    )
  )

  private def shown: HtmlElement = div(
    heading,
    StrokesOf(bus, kana.character, StrokeDemonstration(_).node),
    continueTo(if (stylus) Stage.Guided else Stage.Read)
  )

  private def guided: HtmlElement = div(
    heading,
    p(
      cls := "placement-prompt",
      Tr("Write it over its model.", "Écris-le sur son modèle.")
    ),
    StrokesOf(
      bus,
      kana.character,
      strokes => {
        val exercise =
          WritingExercise(strokes, WritingStep.Guided, canStartAgain = true)
        exercise.node.amend(
          child.maybe <-- exercise.attempts
            .filter(_.completed)
            .map(_ => Some(continueTo(Stage.FromMemory)))
        )
      }
    )
  )

  private def fromMemory: HtmlElement = div(
    p(
      cls := "placement-prompt",
      Tr("Now from memory:", "Maintenant de mémoire :")
    ),
    span(cls := "placement-reading", Kana.shownReading(kana)),
    StrokesOf(
      bus,
      kana.character,
      strokes => {
        val exercise =
          WritingExercise(
            strokes,
            WritingStep.FromMemory,
            canStartAgain = false
          )
        exercise.node.amend(
          child.maybe <-- exercise.attempts.map(attempt =>
            Some(
              button(
                cls := "primary",
                Tr("Continue", "Continuer"),
                onMountFocus,
                onClick.mapTo(SessionAnswer.Writing(attempt)) --> (answer(_))
              )
            )
          )
        )
      }
    )
  )

  private def read: HtmlElement = {
    val question = ReadingQuestion(kana)
    question.node.amend(
      question.answers.map(reading =>
        SessionAnswer.Reading(kana.character, reading.typed, reading.millis)
      ) --> (answer(_))
    )
  }

  override lazy val node: HtmlElement = div(
    cls := "new-kana-lesson",
    child <-- stage.signal.map {
      case Stage.Shown      => shown
      case Stage.Guided     => guided
      case Stage.FromMemory => fromMemory
      case Stage.Read       => read
    }
  )
}
