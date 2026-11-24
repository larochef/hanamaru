package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.kana.Kana
import hanamaru.shared.learner.{ItemStatus, KanaProgress}
import hanamaru.shared.lessons.{LessonPlan, UnitProgress}
import hanamaru.shared.placement.{GrammarPlacementState, PlacementState}

import scala.scalajs.js

import com.raquo.laminar.api.L.*

/** Today: what to do now, as the learner stands, the most useful first. The
  * kana, while they are not settled: the placement test until it is over, then
  * their sessions; once settled, only their reviews when some are due. Then My
  * lesson, the unit under way and what it holds today. Then, smaller, what can
  * be practised or looked up freely. Asked again each time the page is shown.
  */
class HomePage(bus: AppBus) extends ReactiveElement {

  private val shown: EventStream[Unit] =
    bus.events.events.collect { case AppEvent.PageShown(Page.Home) => () }

  private def loaded[A](command: AppCommand)(
      answer: PartialFunction[AppEvent, A]
  ): Signal[Option[A]] =
    shown
      .flatMapSwitch(_ => bus.request(command)(answer))
      .map(_.toOption)
      .startWith(None)

  private val kana: Signal[Option[List[KanaProgress]]] =
    loaded(AppCommand.LoadKanaProgress) {
      case AppEvent.KanaProgressLoaded(progress) => progress
    }

  private val kanaPlacement: Signal[Option[PlacementState]] =
    loaded(AppCommand.LoadPlacement) { case AppEvent.PlacementUpdated(state) =>
      state
    }

  private val grammarPlacement: Signal[Option[GrammarPlacementState]] =
    loaded(AppCommand.LoadGrammarPlacement) {
      case AppEvent.GrammarPlacementUpdated(state) => state
    }

  private val unit: Signal[Option[Option[UnitProgress]]] =
    loaded(AppCommand.LoadUnit) { case AppEvent.UnitLoaded(unit) => unit }

  private val plan: Signal[Option[LessonPlan]] =
    loaded(AppCommand.LoadLessonPlan) { case AppEvent.LessonPlanLoaded(plan) =>
      plan
    }

  private def card(
      title: String,
      content: Modifier[HtmlElement]*
  ): HtmlElement =
    sectionTag(cls := "home-card", h2(title), content)

  private def kanaCard(
      progress: List[KanaProgress],
      placement: PlacementState
  ): Option[HtmlElement] = {
    val known = progress.count(_.status == ItemStatus.Known)
    val toConfirm = progress.count(_.status == ItemStatus.ToConfirm)
    val learning = progress.count(_.status == ItemStatus.Learning)
    val due = KanaProgress.due(progress, js.Date.now().toLong)
    val express = PageLink(
      bus,
      Page.Session,
      cls := "button primary",
      Tr("Express session", "Séance express")
    )
    if (!placement.finished && progress.isEmpty) {
      Some(
        card(
          Tr("The kana", "Les kana"),
          p(
            Tr(
              "Start with the placement test: what you already know will be confirmed quickly instead of learned again. Starting from nothing, an Express session teaches them one by one.",
              "Commence par le test de placement : ce que tu sais déjà sera confirmé rapidement au lieu d'être réappris. Si tu pars de zéro, une séance express te les apprend un à un."
            )
          ),
          div(
            cls := "placement-actions",
            PageLink(
              bus,
              Page.Placement,
              cls := "button primary",
              if (placement.started) Tr("Resume the test", "Reprendre le test")
              else Tr("Take the test", "Passer le test")
            ),
            PageLink(
              bus,
              Page.Session,
              cls := "button",
              Tr("Start from nothing", "Partir de zéro")
            )
          )
        )
      )
    } else if (!KanaProgress.settled(progress)) {
      Some(
        card(
          Tr("The kana, to settle", "Les kana, à consolider"),
          p(
            Tr(
              s"Of the ${Kana.all.size} kana: $known known, $toConfirm to confirm, $learning being learned; $due to review now. A short session a day, ten minutes, until they are known: they will then come back only when due.",
              s"Sur les ${Kana.all.size} kana : $known acquis, $toConfirm à confirmer, $learning en apprentissage ; $due à revoir maintenant. Une courte séance par jour, dix minutes, jusqu'à ce qu'ils soient acquis : ils ne reviendront ensuite qu'à leurs échéances."
            )
          ),
          div(
            cls := "placement-actions",
            express,
            PageLink(
              bus,
              Page.Review,
              cls := "button",
              Tr("Free review", "Révision libre")
            ),
            PageLink(
              bus,
              Page.Chart,
              cls := "button",
              Tr("The chart", "Le tableau")
            )
          )
        )
      )
    } else {
      Option.when(due > 0)(
        card(
          Tr("A few kana to review", "Quelques kana à revoir"),
          p(
            Tr(
              s"$due kana due: a few minutes keep them.",
              s"$due kana arrivés à échéance : quelques minutes suffisent à les garder."
            )
          ),
          div(cls := "placement-actions", express)
        )
      )
    }
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
      Option.when(plan.series)(Tr("a series of words", "une série de mots")) ++
      plan.next.map(point => Tr("the lesson ", "la leçon ") + point.pattern) ++
      Option.when(plan.kanji)(Tr("kanji", "des kanji"))

  private def lessonCard(
      unit: Option[UnitProgress],
      plan: Option[LessonPlan],
      grammarPlacement: Option[GrammarPlacementState],
      kana: List[KanaProgress]
  ): HtmlElement = {
    // most kana never met: words in kana would be hard to read yet
    val kanaFirst = kana.size < Kana.all.size / 2
    card(
      Tr("My lesson", "Ma leçon"),
      unit match {
        case Some(progress) =>
          p(
            Tr("Unit under way: ", "Unité en cours : "),
            span(cls := "unit-theme", progress.theme),
            " · ",
            progress.point.title,
            br(),
            span(
              cls := "in-english",
              Tr(
                s"Words acquired: ${progress.stable} of ${progress.words.size}. ",
                s"Mots acquis : ${progress.stable} sur ${progress.words.size}. "
              ),
              if (progress.practised) Tr("Lesson done.", "Leçon faite.")
              else Tr("Lesson to do.", "Leçon à faire."),
              Option.when(progress.kanji.nonEmpty)(
                Tr(
                  s" Kanji: ${progress.kanjiLearned} of ${progress.kanji.size}.",
                  s" Kanji : ${progress.kanjiLearned} sur ${progress.kanji.size}."
                )
              )
            )
          )
        case None =>
          p(
            Tr(
              "Every unit is acquired.",
              "Toutes les unités sont acquises."
            )
          )
      },
      plan.map(planned).filter(_.nonEmpty).map { parts =>
        p(Tr("Today: ", "Au programme : "), parts.mkString(", "), ".")
      },
      Option.when(kanaFirst)(
        p(
          cls := "in-english",
          Tr(
            "Its words are written in kana: better to know the hiragana first.",
            "Ses mots s'écrivent en kana : mieux vaut connaître les hiragana d'abord."
          )
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
        Option.when(
          grammarPlacement.exists(!_.finished) &&
            unit.exists(progress => !progress.practised)
        )(
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
  }

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
          ": the words due and those of the unit.",
          " : les mots dus et ceux de l'unité."
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
    child <-- kana
      .combineWith(kanaPlacement, grammarPlacement, unit, plan)
      .map {
        case (Some(progress), Some(placement), grammar, Some(current), today) =>
          div(
            kanaCard(progress, placement),
            Option.when(placement.finished || progress.nonEmpty)(
              lessonCard(current, today, grammar, progress)
            )
          )
        case _ => p(cls := "in-english", Tr("Loading…", "Chargement…"))
      },
    freePractice
  )
}
