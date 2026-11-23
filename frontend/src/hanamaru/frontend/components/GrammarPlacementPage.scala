package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.placement.*

import com.raquo.laminar.api.L.*

/** The grammar placement test: what it is, its questions one by one (a sentence
  * with a blank, its French, three choices), then the points it found known and
  * the lesson to start with.
  */
class GrammarPlacementPage(bus: AppBus) extends ReactiveElement {

  private val loaded: EventStream[AppResult[GrammarPlacementState]] =
    bus.events.events
      .collect { case AppEvent.PageShown(Page.GrammarPlacement) => () }
      .flatMapSwitch(_ =>
        bus.request(AppCommand.LoadGrammarPlacement) {
          case AppEvent.GrammarPlacementUpdated(state) =>
            state
        }
      )

  private val updates: EventStream[AppResult[GrammarPlacementState]] =
    EventStream.merge(
      loaded,
      bus.events.events.collect {
        case AppEvent.GrammarPlacementUpdated(state) => Right(state)
        case AppEvent.RequestFailed(
              AppCommand.StartGrammarPlacement |
              _: AppCommand.AnswerGrammarPlacement,
              reason
            ) =>
          Left(reason)
      }
    )

  private val state: Signal[Option[AppResult[GrammarPlacementState]]] =
    updates.map(Some(_)).startWith(None)

  private def answer(placementAnswer: GrammarPlacementAnswer): Unit =
    bus.send(AppCommand.AnswerGrammarPlacement(placementAnswer))

  private def introduction(taken: Boolean): HtmlElement = div(
    p(
      Tr(
        "It finds the grammar points you already know, in the order they are learned: for each, a sentence to complete, two at most. It stops by itself after two points missed in a row. The points you know are not taught again; they come back in a review to confirm it.",
        "Il cherche les points de grammaire que tu connais déjà, dans l'ordre où ils s'apprennent : pour chacun, une phrase à compléter, deux au plus. Il s'arrête de lui-même après deux points ratés d'affilée. Les points que tu connais ne sont pas réenseignés ; ils reviennent en révision pour le confirmer."
      )
    ),
    button(
      cls := "primary",
      if (taken) Tr("Take it again", "Le refaire")
      else Tr("Start", "Commencer"),
      onClick --> (_ => bus.send(AppCommand.StartGrammarPlacement))
    )
  )

  private def asked(question: GrammarQuestion): HtmlElement = div(
    p(cls := "in-english", question.point.title),
    p(
      cls := "placement-prompt",
      Tr("Complete the sentence:", "Complète la phrase :")
    ),
    p(
      cls := "word-sentence",
      lang := "ja",
      question.parts.map(part =>
        span(
          cls("is-asked") := part.marked,
          (if (part.spaceBefore) " " else "") + (if (part.marked) "＿＿"
                                                 else part.text)
        )
      )
    ),
    p(cls := "example-french", question.french),
    div(
      cls := "exercise-choices",
      question.choices.map(choice =>
        button(
          lang := "ja",
          choice,
          onClick --> (_ =>
            answer(
              GrammarPlacementAnswer
                .Chosen(question.point.id, question.number, Some(choice))
            )
          )
        )
      )
    ),
    div(
      cls := "placement-escapes",
      button(
        Tr("I don't know", "Je ne sais pas"),
        onClick --> (_ =>
          answer(
            GrammarPlacementAnswer
              .Chosen(question.point.id, question.number, None)
          )
        )
      ),
      button(
        Tr(
          "I know no Japanese grammar",
          "Je ne connais pas la grammaire japonaise"
        ),
        onClick --> (_ => answer(GrammarPlacementAnswer.GiveUp))
      )
    )
  )

  private def result(state: GrammarPlacementState): HtmlElement = div(
    cls := "placement-result",
    h3(Tr("What the test found", "Ce que le test a trouvé")),
    if (state.results.isEmpty) p(Tr("No point tested.", "Aucun point testé."))
    else
      ul(
        state.results.map(result =>
          li(
            span(
              cls := (if (result.passed) "last-answer is-right"
                      else "last-answer is-failed"),
              if (result.passed) "✓ " else "✗ "
            ),
            result.point.title
          )
        )
      ),
    state.firstLesson match {
      case Some(point) =>
        p(
          Tr("Start with: ", "Commence par : "),
          PageLink(bus, Page.Lesson(point.id), point.title),
          Tr(", or a ", ", ou une "),
          PageLink(
            bus,
            Page.LessonSession,
            Tr("lesson session", "séance Leçon")
          ),
          "."
        )
      case None =>
        p(
          Tr(
            "Every point of the curriculum is known.",
            "Tous les points du programme sont connus."
          )
        )
    },
    introduction(taken = true)
  )

  override lazy val node: HtmlElement = div(
    cls := "placement-page",
    PageLink(bus, Page.Grammar, cls := "back", Tr("← Grammar", "← Grammaire")),
    h1(Tr("Grammar placement test", "Test de placement en grammaire")),
    child <-- state.map {
      case None => p(cls := "in-english", Tr("Loading…", "Chargement…"))
      case Some(Left(reason))               => p(cls := "error", reason)
      case Some(Right(now)) if !now.started => introduction(taken = false)
      case Some(Right(now))                 =>
        now.question match {
          case Some(question) =>
            div(
              p(
                cls := "in-english",
                Tr(
                  s"Points decided: ${now.results.size}",
                  s"Points tranchés : ${now.results.size}"
                )
              ),
              asked(question)
            )
          case None => result(now)
        }
    }
  )
}
