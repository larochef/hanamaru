package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.ReferenceDataState
import hanamaru.shared.lessons.*

import com.raquo.laminar.api.L.*

/** A lesson session: the grammar points due for review, each practised in its
  * exercises, then the next lesson, read and practised; then what was done.
  */
class LessonSessionPage(bus: AppBus) extends ReactiveElement {

  private enum Step {
    case Review(point: GrammarPoint)
    case Learn(point: GrammarPoint)
  }

  private val ready: Signal[Boolean] = bus.events.events
    .collect { case AppEvent.ServerChecked(status) =>
      status.analyser == ReferenceDataState.Ready && status.dictionary == ReferenceDataState.Ready
    }
    .startWith(false)

  private val shown: Signal[Boolean] = bus.events.events
    .collect { case AppEvent.PageShown(page) => page == Page.LessonSession }
    .startWith(false)

  // planned each time the page is shown
  private val plan: Signal[Option[AppResult[List[Step]]]] = shown
    .combineWith(ready)
    .changes
    .distinct
    .flatMapSwitch {
      case (true, true) =>
        bus
          .request(AppCommand.LoadLessonPlan) {
            case AppEvent.LessonPlanLoaded(plan) =>
              plan.reviews
                .map(Step.Review(_)) ++ plan.next.map(Step.Learn(_)).toList
          }
          .map(Some(_))
      case _ => EventStream.fromValue(None)
    }
    .startWith(None)

  private val at = Var(0)

  private def stepView(step: Step, index: Int, total: Int): HtmlElement = {
    val goOn = () => at.set(index + 1)
    step match {
      case Step.Review(point) =>
        div(
          p(
            cls := "in-english",
            Tr(
              s"Step ${index + 1} of $total: a review",
              s"Étape ${index + 1} sur $total : une révision"
            )
          ),
          h2(point.title),
          LessonExercises(bus, point.id, Some(goOn)),
          button(
            Tr("Skip this review", "Passer cette révision"),
            onClick --> (_ => goOn())
          )
        )
      case Step.Learn(point) =>
        val lesson = bus.request(AppCommand.LoadLesson(point.id)) {
          case AppEvent.LessonLoaded(lesson) if lesson.point.id == point.id =>
            lesson
        }
        div(
          p(
            cls := "in-english",
            Tr(
              s"Step ${index + 1} of $total: a new lesson",
              s"Étape ${index + 1} sur $total : une nouvelle leçon"
            )
          ),
          child <-- lesson.map {
            case Right(loaded) => LessonView(bus, loaded, Some(goOn)).node
            case Left(reason)  => p(cls := "error", reason)
          },
          button(
            Tr("End the session", "Terminer la séance"),
            onClick --> (_ => goOn())
          )
        )
    }
  }

  private def summary(steps: List[Step]): HtmlElement = div(
    h2(Tr("Session over", "Séance terminée")),
    if (steps.isEmpty)
      p(
        Tr(
          "Nothing to review, and every lesson is learned.",
          "Rien à réviser, et toutes les leçons sont apprises."
        )
      )
    else
      ul(steps.map {
        case Step.Review(point) =>
          li(Tr("Reviewed: ", "Révisé : "), point.title)
        case Step.Learn(point) => li(Tr("Lesson: ", "Leçon : "), point.title)
      }),
    PageLink(
      bus,
      Page.Grammar,
      Tr("Back to the grammar", "Retour à la grammaire")
    )
  )

  override lazy val node: HtmlElement = div(
    cls := "lesson-session-page",
    h1(Tr("Lesson session", "Séance Leçon")),
    plan.changes --> (_ => at.set(0)),
    child <-- plan.combineWith(at.signal).map {
      case (None, _) => p(cls := "in-english", Tr("Planning…", "Préparation…"))
      case (Some(Left(reason)), _)     => p(cls := "error", reason)
      case (Some(Right(steps)), index) =>
        steps.lift(index).fold(summary(steps))(stepView(_, index, steps.size))
    }
  )
}
