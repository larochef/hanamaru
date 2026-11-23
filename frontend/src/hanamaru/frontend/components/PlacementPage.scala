package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.kana.{Kana, Script}
import hanamaru.shared.placement.*
import hanamaru.shared.writing.WritingStep

import scala.scalajs.js

import com.raquo.laminar.api.L.*

/** The kana placement test: what it is, its questions one by one, then what it
  * found, script by script.
  */
class PlacementPage(bus: AppBus) extends ReactiveElement {

  private val pageShown: EventStream[Unit] = bus.events.events.collect {
    case AppEvent.PageShown(Page.Placement) => ()
  }

  // loaded when the page is shown, then as each command moves it on
  private val loaded: EventStream[AppResult[PlacementState]] =
    pageShown.flatMapSwitch(_ =>
      bus.request(AppCommand.LoadPlacement) {
        case AppEvent.PlacementUpdated(state) => state
      }
    )

  private val state: Signal[Option[PlacementState]] =
    loaded.collect { case Right(state) => Some(state) }.startWith(None)

  private val failure: Signal[Option[String]] = EventStream
    .merge(
      loaded.collect { case Left(reason) => Some(reason) },
      bus.events.events.collect {
        case AppEvent.RequestFailed(
              AppCommand.StartPlacement | _: AppCommand.AnswerPlacement,
              reason
            ) =>
          Some(reason)
        case AppEvent.PlacementUpdated(_) => None
      }
    )
    .startWith(None)

  private def answer(placementAnswer: PlacementAnswer): Unit =
    bus.send(AppCommand.AnswerPlacement(placementAnswer))

  private def scriptName(script: Script): String = script match {
    case Script.Hiragana => Tr("hiragana", "hiragana")
    case Script.Katakana => Tr("katakana", "katakana")
  }

  private def introduction: HtmlElement = div(
    p(
      Tr(
        "It finds what you already know of hiragana and katakana: you read each kana by typing its reading in romaji, and write a few of them with the stylus. It stops by itself when mistakes outweigh right answers. You can stop it and come back to it later.",
        "Il mesure ce que tu sais déjà des hiragana et des katakana : tu lis chaque kana en tapant sa lecture en romaji, et tu en écris quelques-uns au stylet. Il s'arrête de lui-même quand les erreurs l'emportent. Tu peux l'interrompre et le reprendre plus tard."
      )
    ),
    p(
      Tr(
        "What you get right is not trusted blindly: it comes back in a review in a week or so, to be confirmed.",
        "Ce que tu réussis n'est pas acquis d'office : il revient en révision d'ici une semaine environ, pour être confirmé."
      )
    ),
    button(
      cls := "primary",
      Tr("Start", "Commencer"),
      onClick.mapTo(AppCommand.StartPlacement) --> (bus.send(_))
    )
  )

  private def lastReading(state: PlacementState): Option[HtmlElement] =
    state.lastReading.map(last =>
      p(
        cls := (if (last.correct) "last-answer is-right"
                else "last-answer is-failed"),
        span(lang := "ja", last.character),
        if (last.correct) s" : ${last.romaji} ✓"
        else Tr(s" is read ${last.romaji}", s" se lit ${last.romaji}")
      )
    )

  private def reading(state: PlacementState, kana: Kana): HtmlElement = {
    val shownAt = js.Date.now()
    val sent = Var(false)
    def send(typed: Option[String]): Unit = if (!sent.now()) {
      sent.set(true)
      answer(
        PlacementAnswer.Reading(kana.character, typed, js.Date.now() - shownAt)
      )
    }
    val field = input(
      cls := "reading-input",
      placeholder := "romaji",
      autoComplete := "off",
      spellCheck := false,
      onMountFocus
    )
    div(
      cls := "placement-question",
      lastReading(state),
      p(
        cls := "placement-prompt",
        Tr("How is it read?", "Comment se lit-il ?")
      ),
      span(cls := "placement-kana", lang := "ja", kana.character),
      form(
        onSubmit.preventDefault --> { _ =>
          Option(field.ref.value)
            .filter(_.trim.nonEmpty)
            .foreach(typed => send(Some(typed)))
        },
        field,
        button(typ := "submit", cls := "primary", Tr("Answer", "Valider"))
      ),
      div(
        cls := "placement-escapes",
        button(
          Tr("I don't know", "Je ne sais pas"),
          onClick --> (_ => send(None))
        ),
        button(
          cls := "link",
          Tr(
            s"I don't know the ${scriptName(kana.script)} at all",
            s"Je ne connais pas du tout les ${scriptName(kana.script)}"
          ),
          onClick --> { _ =>
            if (!sent.now()) {
              sent.set(true)
              answer(PlacementAnswer.SkipScript(kana.script))
            }
          }
        )
      )
    )
  }

  private def writing(kana: Kana): HtmlElement = div(
    cls := "placement-question",
    p(
      cls := "placement-prompt",
      Tr(
        s"Write from memory, in ${scriptName(kana.script)}:",
        s"Écris de mémoire, en ${scriptName(kana.script)} :"
      )
    ),
    span(cls := "placement-reading", kana.romaji),
    child.maybe <-- bus
      .request(AppCommand.LoadStrokes(kana.character)) {
        case AppEvent.StrokesLoaded(strokes)
            if strokes.character == kana.character =>
          strokes
      }
      .map {
        case Right(strokes) =>
          val exercise =
            WritingExercise(
              strokes,
              WritingStep.FromMemory,
              canStartAgain = false
            )
          Some(
            exercise.node.amend(
              child.maybe <-- exercise.attempts.map(attempt =>
                Some(
                  button(
                    cls := "primary",
                    Tr("Continue", "Continuer"),
                    onMountFocus,
                    onClick
                      .mapTo(PlacementAnswer.Writing(attempt)) --> (answer(_))
                  )
                )
              )
            )
          )
        case Left(reason) =>
          Some(
            p(
              cls := "is-failed",
              Tr(
                s"The strokes could not be loaded: $reason",
                s"Les traits n'ont pas pu être chargés : $reason"
              )
            )
          )
      }
  )

  private def question(
      state: PlacementState,
      asked: PlacementQuestion
  ): HtmlElement =
    asked match {
      case PlacementQuestion.Reading(character) =>
        reading(
          state,
          Kana.find(character).getOrElse(throw IllegalStateException(character))
        )
      case PlacementQuestion.Writing(character) =>
        writing(
          Kana.find(character).getOrElse(throw IllegalStateException(character))
        )
    }

  private def scriptSummary(result: ScriptResult): HtmlElement = div(
    cls := "placement-result",
    h3(scriptName(result.script).capitalize),
    if (result.skipped) {
      p(
        Tr(
          "Not tested: to be learned from the start.",
          "Non testés : à apprendre depuis le début."
        )
      )
    } else {
      p(
        Tr(
          s"Read right: ${result.read} of ${result.readAsked}. Written right: ${result.written} of ${result.writtenAsked}.",
          s"Bien lus : ${result.read} sur ${result.readAsked}. Bien écrits : ${result.written} sur ${result.writtenAsked}."
        )
      )
    },
    Option.when(!result.skipped)(
      p(result.startAt match {
        case Some(character) =>
          span(
            Tr(
              "To learn from the row of ",
              "À apprendre à partir de la ligne de "
            ),
            span(lang := "ja", character),
            "."
          )
        case None =>
          span(
            Tr(
              "All known: the reviews will confirm it.",
              "Tout est connu : les révisions le confirmeront."
            )
          )
      })
    )
  )

  private def summary(state: PlacementState): HtmlElement = div(
    p(Tr("The test is over.", "Le test est terminé.")),
    state.scripts.map(scriptSummary),
    div(
      cls := "placement-actions",
      PageLink(
        bus,
        Page.Chart,
        cls := "button primary",
        Tr("See the chart", "Voir le tableau")
      ),
      button(
        Tr("Take the test again", "Repasser le test"),
        onClick.mapTo(AppCommand.StartPlacement) --> (bus.send(_))
      )
    )
  )

  override lazy val node: HtmlElement = div(
    cls := "placement-page",
    h1(Tr("Kana placement test", "Test de placement des kana")),
    child.maybe <-- failure.map(_.map(reason => p(cls := "is-failed", reason))),
    child <-- state.map {
      case None                          => p(Tr("Loading…", "Chargement…"))
      case Some(state) if !state.started => introduction
      case Some(state)                   =>
        state.question.fold(summary(state))(question(state, _))
    }
  )
}
