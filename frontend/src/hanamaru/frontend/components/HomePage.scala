package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.lessons.LessonPlan
import hanamaru.shared.session.{KanaToday, Today}

import com.raquo.laminar.api.L.*

/** Today: what to do now, as the server decided it from where the learner
  * stands, shown as it is. The kana's placement test while no kana is met, or
  * an Express session when more are due than My lesson takes; then My lesson,
  * the unit under way and what today's session holds; then, smaller, what can
  * be practised or looked up freely. Asked again each time the page is shown.
  */
class HomePage(bus: AppBus) extends ReactiveElement {

  private val today: Signal[Option[AppResult[Today]]] = bus.events.events
    .collect {
      case AppEvent.PageShown(Page.Home) => ()
      // moved on from a unit: today's plan is the next unit's
      case AppEvent.UnitMovedOn(_) => ()
    }
    .flatMapSwitch(_ =>
      bus.request(AppCommand.LoadToday) { case AppEvent.TodayLoaded(today) =>
        today
      }
    )
    .map(Some(_))
    .startWith(None)

  private def card(
      title: String,
      content: Modifier[HtmlElement]*
  ): HtmlElement =
    sectionTag(cls := "home-card", h2(title), content)

  private def kanaCard(kana: KanaToday): HtmlElement = kana match {
    case KanaToday.PlacementToTake(started) =>
      card(
        Tr("The kana", "Les kana"),
        p(
          Tr(
            "Start with the placement test: what you already know will be confirmed quickly instead of learned again. Starting from nothing, My lesson teaches them, a few each time.",
            "Commence par le test de placement : ce que tu sais déjà sera confirmé rapidement au lieu d'être réappris. Si tu pars de zéro, Ma leçon te les apprend, quelques-uns à chaque fois."
          )
        ),
        div(
          cls := "placement-actions",
          PageLink(
            bus,
            Page.Placement,
            cls := "button primary",
            if (started) Tr("Resume the test", "Reprendre le test")
            else Tr("Take the test", "Passer le test")
          )
        )
      )
    case KanaToday.Backlog(due) =>
      card(
        Tr("Many kana to review", "Beaucoup de kana à revoir"),
        p(
          Tr(
            s"$due kana are due, more than My lesson takes: an Express session clears them.",
            s"$due kana sont arrivés à échéance, plus que Ma leçon n'en prend : une séance express les rattrape."
          )
        ),
        div(
          cls := "placement-actions",
          PageLink(
            bus,
            Page.Session,
            cls := "button",
            Tr("Express session", "Séance express")
          )
        )
      )
  }

  private def planned(plan: LessonPlan): List[String] =
    Option
      .when(plan.reviews.nonEmpty)(
        if (plan.reviews.size == 1)
          Tr("a grammar review", "une révision de grammaire")
        else
          Tr(
            s"${plan.reviews.size} grammar reviews",
            s"${plan.reviews.size} révisions de grammaire"
          )
      )
      .toList ++
      plan.series.map(_ => Tr("a series of words", "une série de mots")) ++
      plan.next.map(point => Tr("the lesson ", "la leçon ") + point.pattern) ++
      plan.characters.map(_ => Tr("kana or kanji", "des kana ou kanji"))

  private def lessonCard(today: Today): HtmlElement =
    today.unit
      .flatMap(_.onward)
      .fold(lessonUnderWay(today))(onward =>
        card(
          Tr("My lesson", "Ma leçon"),
          UnitAcquired(
            bus,
            onward,
            PageLink(
              bus,
              Page.LessonSession,
              cls := "button",
              Tr("Go on with this unit", "Continuer sur cette unité")
            ).node
          ).node
        )
      )

  private def lessonUnderWay(today: Today): HtmlElement =
    card(
      Tr("My lesson", "Ma leçon"),
      today.unit.fold(
        p(Tr("Every unit is acquired.", "Toutes les unités sont acquises."))
      )(UnitProgressView(bus, _).node),
      Option.when(planned(today.plan).nonEmpty)(
        p(
          Tr("Today: ", "Au programme : "),
          planned(today.plan).mkString(", "),
          "."
        )
      ),
      div(
        cls := "placement-actions",
        PageLink(
          bus,
          Page.LessonSession,
          cls := "button primary",
          Tr("Start my lesson", "Commencer ma leçon")
        ),
        // a learner with some grammar skips what they know
        Option.when(today.grammarPlacement)(
          PageLink(
            bus,
            Page.GrammarPlacement,
            cls := "button",
            Tr(
              "Some grammar already? Placement test",
              "Déjà des bases ? Test de placement"
            )
          )
        )
      )
    )

  private def freePractice: HtmlElement = sectionTag(
    cls := "home-free",
    h2(Tr("Practise or look up freely", "S'entraîner ou consulter librement")),
    ul(
      li(
        PageLink(
          bus,
          Page.Series,
          Tr("A series of words", "Une série de mots")
        ),
        Tr(
          ": the words due, those of the unit, then any you have met.",
          " : les mots dus, ceux de l'unité, puis n'importe lesquels déjà vus."
        )
      ),
      li(
        PageLink(
          bus,
          Page.FreePractice,
          Tr("Free practice", "Entraînement libre")
        ),
        Tr(
          ": sentences on everything learned so far, to translate both ways.",
          " : des phrases sur tout ce qui est appris, à traduire dans les deux sens."
        )
      ),
      li(
        PageLink(bus, Page.Grammar, Tr("The grammar", "La grammaire")),
        Tr(": every lesson, in order.", " : toutes les leçons, dans l'ordre.")
      ),
      li(
        PageLink(bus, Page.Vocabulary, Tr("The vocabulary", "Le vocabulaire")),
        Tr(": the core words, in order.", " : les mots de base, dans l'ordre.")
      ),
      li(
        PageLink(bus, Page.KanjiChart, Tr("The kanji", "Les kanji")),
        Tr(
          ": by school year, to write freely.",
          " : par année scolaire, à écrire librement."
        )
      ),
      li(
        PageLink(bus, Page.Chart, Tr("The kana chart", "Le tableau des kana")),
        Tr(
          ": each kana, how it is written.",
          " : chaque kana, comment il s'écrit."
        )
      )
    )
  )

  override lazy val node: HtmlElement = div(
    cls := "home-page",
    h1(Tr("Today", "Aujourd'hui")),
    child <-- today.map {
      case Some(Right(shown)) =>
        div(shown.kana.map(kanaCard), lessonCard(shown))
      case Some(Left(reason)) => p(cls := "is-failed", reason)
      case None => p(cls := "in-english", Tr("Loading…", "Chargement…"))
    },
    freePractice
  )
}
