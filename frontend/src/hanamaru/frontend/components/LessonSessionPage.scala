package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.ReferenceDataState
import hanamaru.shared.lessons.*

import com.raquo.laminar.api.L.*

/** My lesson, the unit under way at a glance: where it stands and what it waits
  * for, a session to review and go on ([[LessonSession]], in a pop-in), and the
  * unit's sections ([[UnitSections]]), each with its own practice. The unit is
  * asked again whenever the page is shown, a pop-in closed, or the learner
  * moves on.
  */
class LessonSessionPage(bus: AppBus) extends ReactiveElement {

  private val ready: Signal[Boolean] = bus.events.events
    .collect { case AppEvent.ServerChecked(status) =>
      List(status.analyser, status.dictionary, status.kanji, status.strokes)
        .forall(_ == ReferenceDataState.Ready)
    }
    .startWith(false)

  private val shown: Signal[Boolean] = bus.events.events
    .collect { case AppEvent.PageShown(page) => page == Page.LessonSession }
    .startWith(false)

  private val reviewing = Var(false)

  private val closed = EventBus[Unit]()

  // the unit under way, and gathered, or none once every unit is acquired
  private val unit
      : Signal[Option[AppResult[Option[(UnitProgress, UnitRecap)]]]] =
    EventStream
      .merge(
        shown.combineWith(ready).changes.distinct.collect { case (true, true) =>
          ()
        },
        closed.events,
        bus.events.events.collect { case AppEvent.UnitMovedOn(_) => () }
      )
      .flatMapSwitch(_ =>
        bus
          .request(AppCommand.LoadUnit) { case AppEvent.UnitLoaded(unit) =>
            unit
          }
          .flatMapSwitch {
            case Right(Some(progress)) =>
              val point = progress.point.id
              bus
                .request(AppCommand.LoadUnitRecap(point)) {
                  case AppEvent.UnitRecapLoaded(recap)
                      if recap.point.id == point =>
                    recap
                }
                .map(_.map(recap => Some(progress -> recap)))
            case Right(None)  => EventStream.fromValue(Right(None))
            case Left(reason) => EventStream.fromValue(Left(reason))
          }
          .map(Some(_))
      )
      .startWith(None)

  private def close(): Unit = {
    reviewing.set(false)
    closed.writer.onNext(())
  }

  private def reviewButton: HtmlElement = button(
    cls := "review-button",
    Tr("Review", "Réviser"),
    span(
      cls := "review-button-detail",
      Tr("what is due, then what comes next", "ce qui est dû, puis la suite")
    ),
    onClick --> (_ => reviewing.set(true))
  )

  // a criterion of the unit, met or not as the server says
  private def counted(text: String, met: Boolean): HtmlElement =
    span(cls := "unit-badge", cls("is-met") := met, text)

  private def badges(progress: UnitProgress): List[HtmlElement] = List(
    counted(
      Tr(
        s"Words ${progress.stable}/${progress.words.size} (${progress.wordsNeeded} needed)",
        s"Mots ${progress.stable}/${progress.words.size} (il en faut ${progress.wordsNeeded})"
      ),
      progress.wordsAcquired
    ),
    counted(
      if (progress.practised) Tr("Lesson done", "Leçon faite")
      else Tr("Lesson to do", "Leçon à faire"),
      progress.practised
    )
  ) ++ progress.symbols.map(symbols =>
    counted(
      symbols.kind match {
        case SymbolKind.Hiragana =>
          Tr(
            s"Hiragana ${symbols.learned}/${symbols.total}",
            s"Hiragana ${symbols.learned}/${symbols.total}"
          )
        case SymbolKind.Katakana =>
          Tr(
            s"Katakana ${symbols.learned}/${symbols.total}",
            s"Katakana ${symbols.learned}/${symbols.total}"
          )
        case SymbolKind.Kanji =>
          Tr(
            s"Kanji ${symbols.learned}/${symbols.total} learned",
            s"Kanji ${symbols.learned}/${symbols.total} appris"
          )
      },
      symbols.done
    )
  )

  private def header(progress: UnitProgress, recap: UnitRecap): HtmlElement =
    div(
      cls := "my-lesson-head",
      div(
        cls := "my-lesson-standing",
        div(
          cls := "unit-standing-line",
          UnitsNav(bus, recap).node,
          badges(progress)
        ),
        h1(
          progress.theme,
          " ",
          span(cls := "unit-pattern", lang := "ja", progress.point.pattern)
        ),
        p(cls := "unit-next", UnitProgressView.waiting(progress.waitsFor))
      ),
      reviewButton
    )

  // acquired: the choice to move on, or to go on with it in a session
  private def onward(progress: UnitProgress): Option[HtmlElement] =
    progress.onward.map(onward =>
      UnitAcquired(
        bus,
        onward,
        button(
          Tr("Go on with this unit", "Continuer sur cette unité"),
          onClick --> (_ => reviewing.set(true))
        )
      ).node
    )

  private def page(progress: UnitProgress, recap: UnitRecap): HtmlElement =
    div(
      header(progress, recap),
      onward(progress),
      UnitSections(bus, recap, () => closed.writer.onNext(())).node
    )

  override lazy val node: HtmlElement = div(
    cls := "lesson-session-page",
    child <-- unit.map {
      case None => p(cls := "in-english", Tr("Loading…", "Chargement…"))
      case Some(Left(reason))                   => p(cls := "error", reason)
      case Some(Right(Some((progress, recap)))) => page(progress, recap)
      case Some(Right(None))                    =>
        div(
          h1(Tr("My lesson", "Ma leçon")),
          p(Tr("Every unit is acquired.", "Toutes les unités sont acquises.")),
          reviewButton
        )
    },
    child.maybe <-- reviewing.signal.map(
      Option.when(_)(
        Modal(
          Tr("My lesson", "Ma leçon"),
          LessonSession(bus).node,
          () => close()
        ).node
      )
    )
  )
}
