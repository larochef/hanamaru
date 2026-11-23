package hanamaru.frontend.components

import hanamaru.frontend.i18n.{Language, Tr}
import hanamaru.frontend.model.*
import hanamaru.shared.ReferenceDataState
import hanamaru.shared.dictionary.{DictionaryWord, Meaning}

import com.raquo.laminar.api.L.*

/** The core vocabulary, in the order it is learned (by JLPT level, N5 first):
  * each with its forms, its reading and its meanings, in French when JMdict has
  * them, else in English, said so.
  */
class VocabularyPage(bus: AppBus) extends ReactiveElement {
  private val pageSize = 50

  private val ready: Signal[Boolean] = bus.events.events
    .collect { case AppEvent.ServerChecked(status) =>
      status.dictionary == ReferenceDataState.Ready
    }
    .startWith(false)

  private val more = EventBus[Int]()

  // the first page once the dictionary is ready, then each page asked for
  private val pages: EventStream[AppResult[(Int, List[DictionaryWord])]] =
    EventStream
      .merge(ready.changes.filter(identity).mapTo(0), more.events)
      .flatMapSwitch(from =>
        bus.request(AppCommand.LoadCoreWords(from, pageSize)) {
          case AppEvent.CoreWordsLoaded(`from`, words) => from -> words
        }
      )

  private val words: Signal[List[DictionaryWord]] = pages
    .collect { case Right((from, page)) => from -> page }
    .scanLeft(List.empty[DictionaryWord]) { case (shown, (from, page)) =>
      shown.take(from) ++ page
    }

  // French when JMdict has it, else English, said so
  private def meanings(word: DictionaryWord): (List[Meaning], Boolean) = {
    val french = word.meanings.filter(_.language == "fre")
    if (french.nonEmpty && Language.current.code == "fr") (french, false)
    else
      (word.meanings.filter(_.language == "eng"), Language.current.code == "fr")
  }

  private def row(word: DictionaryWord): HtmlElement = {
    val (shown, inEnglish) = meanings(word)
    val written = if (word.usuallyKana) Nil else word.writings.take(1)
    tr(
      td(
        cls := "word-forms",
        written.map(form => span(cls := "word-writing", lang := "ja", form)),
        span(
          cls := "word-reading",
          lang := "ja",
          word.readings.headOption.getOrElse("")
        )
      ),
      td(
        cls := "word-meanings",
        shown
          .take(3)
          .map(meaning => div(meaning.glosses.take(4).mkString(", "))),
        Option.when(inEnglish)(span(cls := "in-english", Tr("", "en anglais")))
      ),
      td(cls := "word-level", s"N${word.level}")
    )
  }

  override lazy val node: HtmlElement = div(
    cls := "vocabulary-page",
    div(
      cls := "review-bar",
      h1(Tr("Vocabulary", "Vocabulaire")),
      PageLink(
        bus,
        Page.Series,
        cls := "button primary",
        Tr("Rapid series", "Série rapide")
      )
    ),
    p(
      cls := "placement-prompt",
      Tr(
        "The words are learned in this order: by JLPT level, N5 first, and within a level the most frequent first.",
        "Les mots s'apprennent dans cet ordre : par niveau JLPT, du N5 au N1, et dans chaque niveau les plus fréquents d'abord."
      )
    ),
    child.maybe <-- ready.map(isReady =>
      Option.when(!isReady)(
        p(
          Tr("The dictionary is being prepared…", "Le dictionnaire se prépare…")
        )
      )
    ),
    child.maybe <-- pages.map(
      _.left.toOption.map(reason => p(cls := "is-failed", reason))
    ),
    table(
      cls := "vocabulary",
      tbody(children <-- words.map(_.map(row)))
    ),
    child.maybe <-- words.map(shown =>
      Option.when(shown.nonEmpty && shown.size % pageSize == 0)(
        button(
          Tr("More words", "Plus de mots"),
          onClick.mapTo(shown.size) --> more.writer
        )
      )
    )
  )
}
