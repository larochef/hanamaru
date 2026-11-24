package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.ReferenceDataState
import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.kanji.KanjiDetail
import hanamaru.shared.writing.WritingStep

import com.raquo.laminar.api.L.*

/** One kanji: its meanings, its readings (on in katakana, kun in hiragana), its
  * strokes shown, written guided, then from memory, and words of the core
  * vocabulary written with it. Not followed yet: nothing is recorded.
  */
class KanjiPage(bus: AppBus) extends ReactiveElement {

  private val shown: Signal[Option[String]] = bus.events.events
    .collect {
      case AppEvent.PageShown(Page.KanjiCharacter(literal)) => Some(literal)
      case AppEvent.PageShown(_)                            => None
    }
    .startWith(None)

  private val ready: Signal[Boolean] = bus.events.events
    .collect { case AppEvent.ServerChecked(status) =>
      status.kanji == ReferenceDataState.Ready && status.strokes == ReferenceDataState.Ready
    }
    .startWith(false)

  private val detail: Signal[Option[AppResult[KanjiDetail]]] = shown
    .combineWith(ready)
    .changes
    .distinct
    .flatMapSwitch {
      case (Some(literal), true) =>
        bus
          .request(AppCommand.LoadKanji(literal)) {
            case AppEvent.KanjiLoaded(detail)
                if detail.kanji.literal == literal =>
              detail
          }
          .map(Some(_))
      case _ => EventStream.fromValue(None)
    }
    .startWith(None)

  private def word(each: DictionaryWord): HtmlElement = {
    val french = each.meanings.filter(_.language == "fre")
    val meanings =
      if (french.nonEmpty) french else each.meanings.filter(_.language == "eng")
    div(
      cls := "unit-word",
      span(
        cls := "word-writing",
        lang := "ja",
        each.writings.headOption.getOrElse("")
      ),
      span(
        cls := "word-reading",
        lang := "ja",
        each.readings.headOption.getOrElse("")
      ),
      span(
        cls := "unit-meaning",
        meanings.headOption.fold("")(_.glosses.take(3).mkString(", "))
      )
    )
  }

  private def writing(literal: String): HtmlElement = {
    val step = Var(WritingStep.Demonstration)
    div(
      cls := "kanji-writing",
      div(
        cls := "step-tabs",
        List(
          WritingStep.Demonstration -> Tr("Strokes", "Traits"),
          WritingStep.Guided -> Tr("Guided", "Guidé"),
          WritingStep.FromMemory -> Tr("From memory", "De mémoire")
        ).map((each, name) =>
          button(
            cls("is-selected") <-- step.signal.map(_ == each),
            name,
            onClick.mapTo(each) --> step.writer
          )
        )
      ),
      child <-- step.signal.map(now =>
        StrokesOf(
          bus,
          literal,
          strokes =>
            if (now == WritingStep.Demonstration)
              StrokeDemonstration(strokes).node
            else WritingExercise(strokes, now, canStartAgain = true).node
        ).node
      )
    )
  }

  private def view(shownDetail: KanjiDetail): HtmlElement = {
    val kanji = shownDetail.kanji
    div(
      div(
        cls := "kanji-heading",
        span(cls := "kanji-model", lang := "ja", kanji.literal),
        div(
          div(cls := "kanji-meanings", kanji.meanings.take(5).mkString(", ")),
          Option.when(kanji.inEnglish)(
            span(cls := "in-english", Tr("", "en anglais"))
          ),
          Option.when(kanji.on.nonEmpty)(
            p(
              Tr("On readings: ", "Lectures on : "),
              span(
                lang := "ja",
                cls := "kanji-readings",
                kanji.on.mkString("・")
              )
            )
          ),
          Option.when(kanji.kun.nonEmpty)(
            p(
              Tr("Kun readings: ", "Lectures kun : "),
              span(
                lang := "ja",
                cls := "kanji-readings",
                kanji.kun.mkString("・")
              )
            )
          ),
          p(
            cls := "in-english",
            Tr(s"${kanji.strokes} strokes", s"${kanji.strokes} traits"),
            kanji.grade
              .filter(_ <= 6)
              .map(grade => Tr(s" · school year $grade", s" · année $grade"))
          )
        )
      ),
      writing(kanji.literal),
      Option.when(shownDetail.words.nonEmpty)(
        div(
          h2(Tr("Words written with it", "Mots qui l'emploient")),
          div(cls := "unit-words", shownDetail.words.map(word))
        )
      )
    )
  }

  override lazy val node: HtmlElement = div(
    cls := "kanji-page",
    PageLink(bus, Page.KanjiChart, cls := "back", Tr("← Kanji", "← Kanji")),
    child <-- detail.map {
      case Some(Right(loaded)) => view(loaded)
      case Some(Left(reason))  => p(cls := "error", reason)
      case None => p(cls := "in-english", Tr("Loading…", "Chargement…"))
    }
  )
}
