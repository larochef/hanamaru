package hanamaru.frontend.components

import hanamaru.frontend.i18n.{Delays, Tr}
import hanamaru.frontend.model.*
import hanamaru.shared.ReferenceDataState
import hanamaru.shared.kana.{Kana, Script}
import hanamaru.shared.writing.*

import com.raquo.laminar.api.L.*

/** One kana practised: the character in the textbook style, its reading, and
  * its strokes, shown, then written guided, then from memory. Written from
  * memory, it is a review of the kana's writing.
  */
class KanaPractice(bus: AppBus, shown: Signal[Option[Kana]])
    extends ReactiveElement {

  private val ready: Signal[Boolean] = bus.events.events
    .collect { case AppEvent.ServerChecked(status) =>
      status.strokes == ReferenceDataState.Ready
    }
    .startWith(false)

  // Which step is shown is the page's own: each kana starts with the
  // demonstration.
  private val step = Var(WritingStep.Demonstration)

  private val strokes: Signal[Option[AppResult[CharacterStrokes]]] =
    shown
      .combineWith(ready)
      .flatMapSwitch {
        case (Some(kana), true) =>
          bus
            .request(AppCommand.LoadStrokes(kana.character)) {
              case AppEvent.StrokesLoaded(strokes)
                  if strokes.character == kana.character =>
                strokes
            }
            .map(Some(_))
        case _ => EventStream.fromValue(None)
      }
      .startWith(None)

  private def exercise(
      loaded: Option[AppResult[CharacterStrokes]],
      shownStep: WritingStep
  ): Option[HtmlElement] = loaded.map {
    case Right(strokes) =>
      shownStep match {
        case WritingStep.Demonstration => StrokeDemonstration(strokes).node
        case _                         =>
          val exercise =
            WritingExercise(strokes, shownStep, canStartAgain = true)
          exercise.node.amend(child.maybe <-- recorded(exercise.attempts))
      }
    case Left(reason) =>
      p(
        cls := "is-failed",
        Tr(
          s"The strokes could not be loaded: $reason",
          s"Les traits n'ont pas pu être chargés : $reason"
        )
      )
  }

  // each attempt kept, and when the character comes back if it was a review
  private def recorded(
      attempts: EventStream[WritingAttempt]
  ): EventStream[Option[HtmlElement]] =
    attempts.flatMapSwitch(attempt =>
      bus
        .request(AppCommand.RecordWriting(attempt)) {
          case AppEvent.WritingRecorded(`attempt`, review) => review
        }
        .map {
          case Right(None)         => None
          case Right(Some(review)) =>
            Some(
              p(
                cls := "next-review",
                Tr("Next review: ", "Prochaine révision : ") +
                  Delays.until(review.due)
              )
            )
          case Left(reason) =>
            Some(
              p(
                cls := "is-failed",
                Tr(
                  s"The attempt could not be kept: $reason",
                  s"L'essai n'a pas pu être gardé : $reason"
                )
              )
            )
        }
    )

  private def stepName(shownStep: WritingStep): String = shownStep match {
    case WritingStep.Demonstration => Tr("Demonstration", "Démonstration")
    case WritingStep.Guided        => Tr("Guided", "Guidé")
    case WritingStep.FromMemory    => Tr("From memory", "De mémoire")
  }

  private def stepTabs: HtmlElement = div(
    cls := "step-tabs",
    WritingStep.values.toList.map(each =>
      button(
        cls("is-selected") <-- step.signal.map(_ == each),
        stepName(each),
        onClick.mapTo(each) --> step.writer
      )
    )
  )

  private def scriptName(script: Script): String = script match {
    case Script.Hiragana => Tr("hiragana", "hiragana")
    case Script.Katakana => Tr("katakana", "katakana")
  }

  // From memory, the character itself is hidden: its reading is the prompt.
  private def heading(kana: Kana): HtmlElement = div(
    cls := "character-heading",
    span(
      cls := "character-model",
      lang := "ja",
      visibility <-- step.signal.map(each =>
        if (each == WritingStep.FromMemory) "hidden" else "visible"
      ),
      kana.character
    ),
    div(
      span(cls := "character-reading", kana.romaji),
      span(cls := "character-script", scriptName(kana.script))
    )
  )

  override lazy val node: HtmlElement = div(
    cls := "kana-practice",
    shown.changes.mapTo(WritingStep.Demonstration) --> step.writer,
    child.maybe <-- shown.map(_.map(heading)),
    stepTabs,
    child.maybe <-- strokes.combineWith(step.signal).map(exercise)
  )
}
