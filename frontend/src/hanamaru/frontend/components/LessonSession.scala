package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.characters.CharactersOf
import hanamaru.shared.lessons.*
import hanamaru.shared.vocabulary.SeriesOf

import com.raquo.laminar.api.L.*

/** A session on the unit under way, in My lesson's pop-in: the grammar points
  * due for review, each practised in its exercises; a series of words, the
  * unit's new ones among them; then the unit's lesson, read and practised, when
  * it was never learned; then the kana and kanji due, and the unit's new ones;
  * then what was done, and where the unit stands: acquired, the choice to move
  * on to the next unit or to go on with this one, each starting a session
  * again. Planned once shown.
  */
class LessonSession(bus: AppBus) extends ReactiveElement {

  private enum Step {
    case Review(point: GrammarPoint)
    case Series(of: SeriesOf)
    case Learn(point: GrammarPoint)
    case Characters(of: CharactersOf)
  }

  // a session again: going on with an acquired unit
  private val again = EventBus[Unit]()

  // planned once shown, and again once moved on from a unit or going on with
  // it
  private val plan: Signal[Option[AppResult[List[Step]]]] = EventStream
    .merge(
      EventStream.fromValue(()),
      again.events,
      bus.events.events.collect { case AppEvent.UnitMovedOn(_) => () }
    )
    .flatMapSwitch(_ =>
      bus
        .request(AppCommand.LoadLessonPlan) {
          case AppEvent.LessonPlanLoaded(plan) =>
            plan.reviews.map(Step.Review(_)) ++
              plan.series.map(Step.Series(_)) ++
              plan.next.map(Step.Learn(_)) ++
              plan.characters.map(Step.Characters(_))
        }
        .map(Some(_))
    )
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
      case Step.Series(of) =>
        div(
          p(
            cls := "in-english",
            Tr(
              s"Step ${index + 1} of $total: the words",
              s"Étape ${index + 1} sur $total : les mots"
            )
          ),
          VocabularySeries(
            bus,
            EventStream.fromValue(()),
            of,
            Some(goOn)
          ),
          button(
            Tr("Skip the words", "Passer les mots"),
            onClick --> (_ => goOn())
          )
        )
      case Step.Characters(of) =>
        div(
          p(
            cls := "in-english",
            Tr(
              s"Step ${index + 1} of $total: kana and kanji",
              s"Étape ${index + 1} sur $total : kana et kanji"
            )
          ),
          CharacterPractice(bus, goOn, of),
          button(
            Tr("Skip the kana and kanji", "Passer les kana et kanji"),
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

  // the unit as the session left it: acquired, the choice to move on
  private def acquired: HtmlElement = div(
    child.maybe <-- bus
      .request(AppCommand.LoadUnit) { case AppEvent.UnitLoaded(unit) => unit }
      .map(
        _.toOption.flatten
          .flatMap(_.onward)
          .map(onward =>
            UnitAcquired(
              bus,
              onward,
              button(
                Tr("Go on with this unit", "Continuer sur cette unité"),
                onClick --> (_ => again.writer.onNext(()))
              )
            ).node
          )
      )
  )

  private def summary(steps: List[Step]): HtmlElement = div(
    acquired,
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
        case Step.Series(of) =>
          li(Tr("A series of words", "Une série de mots"))
        case Step.Learn(point)  => li(Tr("Lesson: ", "Leçon : "), point.title)
        case Step.Characters(_) => li(Tr("Kana and kanji", "Des kana et kanji"))
      }),
    button(
      cls := "primary",
      Tr("Another session", "Une autre séance"),
      onClick --> (_ => again.writer.onNext(()))
    )
  )

  override lazy val node: HtmlElement = div(
    cls := "lesson-session",
    plan.changes --> (_ => at.set(0)),
    child <-- plan.combineWith(at.signal).map {
      case (None, _) => p(cls := "in-english", Tr("Planning…", "Préparation…"))
      case (Some(Left(reason)), _)     => p(cls := "error", reason)
      case (Some(Right(steps)), index) =>
        steps.lift(index).fold(summary(steps))(stepView(_, index, steps.size))
    }
  )
}
