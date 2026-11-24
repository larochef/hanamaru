package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.lessons.UnitProgress

import com.raquo.laminar.api.L.*

/** The unit under way, where its words come from: its theme, its lesson, how
  * many of its words are acquired, and what opens the next one. Asked again
  * whenever `refresh` fires.
  */
class UnitBanner(bus: AppBus, refresh: EventStream[Unit])
    extends ReactiveElement {

  private val unit: Signal[Option[Option[UnitProgress]]] = refresh
    .flatMapSwitch(_ =>
      bus.request(AppCommand.LoadUnit) { case AppEvent.UnitLoaded(unit) =>
        unit
      }
    )
    .collect { case Right(unit) => Some(unit) }
    .startWith(None)

  private def shown(progress: UnitProgress): HtmlElement = {
    val needed = (progress.words.size * 0.8).ceil.toInt
    div(
      cls := "unit-banner",
      p(
        Tr("Unit under way: ", "Unité en cours : "),
        span(cls := "unit-theme", progress.theme),
        " · ",
        PageLink(bus, Page.Lesson(progress.point.id), progress.point.title)
      ),
      p(
        cls := "in-english",
        Tr(
          s"Words acquired: ${progress.stable} of ${progress.words.size} (${needed} needed). ",
          s"Mots acquis : ${progress.stable} sur ${progress.words.size} (il en faut ${needed}). "
        ),
        if (progress.practised) Tr("Lesson done. ", "Leçon faite. ")
        else Tr("Lesson to do. ", "Leçon à faire. "),
        Option.when(progress.kanji.nonEmpty)(
          span(
            Tr("Its kanji: ", "Ses kanji : "),
            span(lang := "ja", progress.kanji.mkString(" ")),
            Tr(
              s" (${progress.kanjiLearned} of ${progress.kanji.size} learned).",
              s" (${progress.kanjiLearned} appris sur ${progress.kanji.size})."
            )
          )
        )
      ),
      Option.when(progress.stable >= needed && progress.practised)(
        p(
          cls := "unit-next",
          Tr(
            "Words and lesson done: the kanji, read and written, open the next unit.",
            "Mots et leçon faits : les kanji, lus et écrits, ouvriront l'unité suivante."
          )
        )
      ),
      Option.when(progress.stable >= needed && !progress.practised)(
        p(
          cls := "unit-next",
          Tr(
            "The words are acquired: do the lesson to open the next unit.",
            "Les mots sont acquis : fais la leçon pour ouvrir l'unité suivante."
          )
        )
      )
    )
  }

  override lazy val node: HtmlElement = div(
    child <-- unit.map {
      case Some(Some(progress)) => shown(progress)
      case _                    => emptyNode
    }
  )
}
