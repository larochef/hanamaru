package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.kana.{Kana, Script}
import hanamaru.shared.session.*
import hanamaru.shared.writing.WritingStep

import scala.scalajs.js

import com.raquo.laminar.api.L.*

/** An Express session: the time available and the context declared, then the
  * exercises the server plans one after the other, until the time is up or the
  * learner stops; then what it did.
  */
class SessionPage(bus: AppBus) extends ReactiveElement {
  private val durations = List(10, 15, 20)

  private val minutes = Var(10)
  private val stylus = Var(true)
  private val startedAgain = EventBus[Unit]()

  private val state: Signal[Option[SessionState]] = EventStream
    .merge(
      bus.events.events.collect {
        case AppEvent.PageShown(Page.Session) => None
        case AppEvent.SessionUpdated(state)   => Some(state)
      },
      startedAgain.events.mapTo(None)
    )
    .startWith(None)

  private val failure: Signal[Option[String]] = bus.events.events
    .collect {
      case AppEvent.RequestFailed(
            _: AppCommand.StartSession | _: AppCommand.AnswerSession |
            _: AppCommand.EndSession,
            reason
          ) =>
        Some(reason)
      case AppEvent.SessionUpdated(_) => None
    }
    .startWith(None)

  private def answer(session: Long, sessionAnswer: SessionAnswer): Unit =
    bus.send(AppCommand.AnswerSession(session, sessionAnswer))

  private def scriptName(script: Script): String = script match {
    case Script.Hiragana => Tr("hiragana", "hiragana")
    case Script.Katakana => Tr("katakana", "katakana")
  }

  private def kanaOf(character: String): Kana =
    Kana.find(character).getOrElse(throw IllegalStateException(character))

  private def startForm: HtmlElement = div(
    cls := "session-start",
    p(Tr("How much time do you have?", "Combien de temps as-tu ?")),
    div(
      cls := "step-tabs",
      durations.map(each =>
        button(
          cls("is-selected") <-- minutes.signal.map(_ == each),
          s"$each min",
          onClick.mapTo(each) --> minutes.writer
        )
      )
    ),
    label(
      cls := "session-context",
      input(
        typ := "checkbox",
        checked <-- stylus.signal,
        onInput.mapToChecked --> stylus.writer
      ),
      Tr("The stylus is at hand", "J'ai le stylet sous la main")
    ),
    p(
      cls := "placement-prompt",
      Tr(
        "The reviews due come first, then a few new kana if there is time left.",
        "Les révisions dues d'abord, puis quelques nouveaux kana s'il reste du temps."
      )
    ),
    button(
      cls := "primary",
      Tr("Start", "Commencer"),
      onClick --> (_ =>
        bus.send(
          AppCommand.StartSession(SessionStart(minutes.now(), stylus.now()))
        )
      )
    )
  )

  private def lastReading(state: SessionState): Option[HtmlElement] =
    state.lastReading.map(last =>
      p(
        cls := (if (last.correct) "last-answer is-right"
                else "last-answer is-failed"),
        span(lang := "ja", last.character),
        if (last.correct) s" : ${last.romaji} ✓"
        else Tr(s" is read ${last.romaji}", s" se lit ${last.romaji}")
      )
    )

  private def remaining(endsAt: Long): String = {
    val minutesLeft = ((endsAt - js.Date.now()) / 60000).ceil.toLong
    if (minutesLeft <= 0) Tr("Time is up", "Temps écoulé")
    else Tr(s"$minutesLeft min left", s"Encore $minutesLeft min")
  }

  private def reading(session: Long, kana: Kana): HtmlElement = {
    val question = ReadingQuestion(kana)
    question.node.amend(
      question.answers.map(reading =>
        SessionAnswer.Reading(kana.character, reading.typed, reading.millis)
      ) --> (answer(session, _))
    )
  }

  private def writing(session: Long, kana: Kana): HtmlElement = div(
    p(
      cls := "placement-prompt",
      Tr(
        s"Write from memory, in ${scriptName(kana.script)}:",
        s"Écris de mémoire, en ${scriptName(kana.script)} :"
      )
    ),
    span(cls := "placement-reading", kana.romaji),
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
                onClick.mapTo(SessionAnswer.Writing(attempt)) --> (answer(
                  session,
                  _
                ))
              )
            )
          )
        )
      }
    )
  )

  private def underWay(
      state: SessionState,
      exercise: SessionExercise
  ): HtmlElement = div(
    div(
      cls := "review-bar",
      h1(Tr("Express session", "Séance express")),
      span(
        cls := "review-why",
        child.text <-- EventStream
          .periodic(15000)
          .map(_ => remaining(state.endsAt))
      ),
      button(
        Tr("Stop", "Arrêter"),
        onClick.mapTo(AppCommand.EndSession(state.id)) --> (bus.send(_))
      )
    ),
    lastReading(state),
    exercise match {
      case SessionExercise.Reading(character) =>
        reading(state.id, kanaOf(character))
      case SessionExercise.Writing(character) =>
        writing(state.id, kanaOf(character))
      case SessionExercise.NewKana(character, withStylus) =>
        NewKanaLesson(bus, state.id, kanaOf(character), withStylus).node
    }
  )

  private def summary(state: SessionState): HtmlElement = div(
    h1(Tr("Session over", "Séance terminée")),
    lastReading(state),
    p(
      Tr(
        s"Reviews: ${state.summary.reviewed}, right: ${state.summary.right}.",
        s"Révisions : ${state.summary.reviewed}, réussies : ${state.summary.right}."
      )
    ),
    Option.when(state.summary.introduced.nonEmpty)(
      p(
        Tr("New kana: ", "Nouveaux kana : "),
        span(lang := "ja", state.summary.introduced.mkString(" "))
      )
    ),
    div(
      cls := "placement-actions",
      PageLink(
        bus,
        Page.Chart,
        cls := "button primary",
        Tr("See the chart", "Voir le tableau")
      ),
      button(
        Tr("Another session", "Nouvelle séance"),
        onClick.mapTo(()) --> startedAgain.writer
      )
    )
  )

  override lazy val node: HtmlElement = div(
    cls := "session-page",
    child.maybe <-- failure.map(_.map(reason => p(cls := "is-failed", reason))),
    child <-- state.map {
      case None =>
        div(h1(Tr("Express session", "Séance express")), startForm)
      case Some(state) =>
        state.exercise.fold(summary(state))(underWay(state, _))
    }
  )
}
