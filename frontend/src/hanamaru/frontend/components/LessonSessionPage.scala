package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.ReferenceDataState
import hanamaru.shared.lessons.*

import com.raquo.laminar.api.L.*

/** My lesson, a session on the unit under way: the grammar points due for
  * review, each practised in its exercises; a series of words, the unit's new
  * ones among them; then the unit's lesson, read and practised, when it was
  * never learned; then the kanji, read and written in known words; then what
  * was done, and where the unit stands.
  */
class LessonSessionPage(bus: AppBus) extends ReactiveElement {

  private enum Step {
    case Review(point: GrammarPoint)
    case Series
    case Learn(point: GrammarPoint)
    case Kanji
  }

  private val ready: Signal[Boolean] = bus.events.events
    .collect { case AppEvent.ServerChecked(status) =>
      List(status.analyser, status.dictionary, status.kanji, status.strokes)
        .forall(_ == ReferenceDataState.Ready)
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
              plan.reviews.map(Step.Review(_)) ++
                Option.when(plan.series)(Step.Series) ++
                plan.next.map(Step.Learn(_)) ++
                Option.when(plan.kanji)(Step.Kanji)
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
      case Step.Series =>
        div(
          p(
            cls := "in-english",
            Tr(
              s"Step ${index + 1} of $total: the words",
              s"Étape ${index + 1} sur $total : les mots"
            )
          ),
          VocabularySeries(bus, EventStream.fromValue(()), Some(goOn)),
          button(
            Tr("Skip the words", "Passer les mots"),
            onClick --> (_ => goOn())
          )
        )
      case Step.Kanji =>
        div(
          p(
            cls := "in-english",
            Tr(
              s"Step ${index + 1} of $total: the kanji",
              s"Étape ${index + 1} sur $total : les kanji"
            )
          ),
          KanjiPractice(bus, goOn),
          button(
            Tr("Skip the kanji", "Passer les kanji"),
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
            Tr("Skip the lesson", "Passer la leçon"),
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
          "Nothing to review or practise, and every lesson is learned.",
          "Rien à réviser ni à pratiquer, et toutes les leçons sont apprises."
        )
      )
    else
      ul(steps.map {
        case Step.Review(point) =>
          li(Tr("Reviewed: ", "Révisé : "), point.title)
        case Step.Series =>
          li(Tr("A series of words", "Une série de mots"))
        case Step.Learn(point) => li(Tr("Lesson: ", "Leçon : "), point.title)
        case Step.Kanji        => li(Tr("Kanji", "Des kanji"))
      }),
    div(
      cls := "placement-actions",
      PageLink(
        bus,
        Page.Series,
        cls := "button primary",
        Tr("Another series", "Encore une série")
      ),
      PageLink(
        bus,
        Page.Grammar,
        cls := "button",
        Tr("The grammar", "La grammaire")
      )
    )
  )

  override lazy val node: HtmlElement = div(
    cls := "lesson-session-page",
    h1(Tr("My lesson", "Ma leçon")),
    // where the unit stands, and again once the session is over
    UnitBanner(
      bus,
      EventStream.merge(
        plan.changes.collect { case Some(Right(_)) => () },
        at.signal.changes
          .withCurrentValueOf(plan)
          .collect {
            case (index, Some(Right(steps))) if index >= steps.size => ()
          }
      )
    ),
    plan.changes --> (_ => at.set(0)),
    child <-- plan.combineWith(at.signal).map {
      case (None, _) => p(cls := "in-english", Tr("Planning…", "Préparation…"))
      case (Some(Left(reason)), _)     => p(cls := "error", reason)
      case (Some(Right(steps)), index) =>
        steps.lift(index).fold(summary(steps))(stepView(_, index, steps.size))
    }
  )
}
