package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.lessons.*
import hanamaru.shared.text.PartWord

import com.raquo.laminar.api.L.*

/** A grammar point's lesson as shown: its explanation, in French, its examples
  * in kana, the grammar they show marked, a reading text, and its exercises;
  * `onExercisesDone` offers to go on once they are done (in a session).
  */
class LessonView(
    bus: AppBus,
    lesson: GrammarLesson,
    onExercisesDone: Option[() => Unit] = None
) extends ReactiveElement {

  // Japanese in the explanation, set apart from the French around it
  private val japanese = "[぀-ヿ一-鿿〜・ー]+(?: [぀-ヿ一-鿿〜・ー]+)*".r

  private def paragraph(text: String): HtmlElement = {
    val runs = japanese.findAllMatchIn(text).toList
    val pieces = runs
      .foldLeft((0, List.empty[Modifier[HtmlElement]])) {
        case ((from, done), run) =>
          (
            run.end,
            done :+ textToTextNode(text.substring(from, run.start)) :+
              span(cls := "in-japanese", lang := "ja", run.matched)
          )
      }
    p(pieces._2, text.substring(pieces._1))
  }

  // A sentence in kana, the point's grammar marked; a word of the dictionary
  // is shown, tapped, in `selected`.
  private def sentence(
      shown: LessonExample,
      selected: Var[Option[PartWord]]
  ): HtmlElement = p(
    cls := "word-sentence",
    lang := "ja",
    shown.parts.map(part =>
      List[Modifier[HtmlElement]](
        Option.when(part.spaceBefore)(" "),
        span(
          cls("is-asked") := part.marked,
          cls("is-word") := part.word.nonEmpty,
          part.text,
          part.word.map(word => onClick --> (_ => selected.set(Some(word))))
        )
      )
    )
  )

  private def example(
      shown: LessonExample,
      selected: Var[Option[PartWord]]
  ): HtmlElement = div(
    cls := "lesson-example",
    sentence(shown, selected),
    p(cls := "example-french", shown.french)
  )

  // the word tapped: its dictionary form, reading and meanings
  private def wordShown(selected: Var[Option[PartWord]]): HtmlElement = div(
    cls := "word-gloss",
    hidden <-- selected.signal.map(_.isEmpty),
    child <-- selected.signal.map {
      case Some(word) =>
        div(
          span(cls := "gloss-reading", lang := "ja", word.reading),
          Option.when(word.form != word.reading)(
            span(cls := "gloss-form", lang := "ja", word.form)
          ),
          Option.when(word.french.nonEmpty)(div(word.french)),
          div(cls := "in-english", word.english),
          button(Tr("Close", "Fermer"), onClick --> (_ => selected.set(None)))
        )
      case None => emptyNode
    }
  )

  // a reading text, asked of the tutor and checked by the code
  private def readingText(
      point: String,
      selected: Var[Option[PartWord]]
  ): HtmlElement = {
    val asked = EventBus[Unit]()
    val text: Signal[Option[AppResult[Option[LessonText]]]] = asked.events
      .flatMapSwitch(_ =>
        EventStream.merge(
          EventStream.fromValue(None),
          bus
            .request(AppCommand.WriteLessonText(point)) {
              case AppEvent.LessonTextWritten(`point`, text) => text
            }
            .map(Some(_))
        )
      )
      .startWith(None)
    val writing = Var(false)
    div(
      cls := "lesson-text",
      h2(Tr("Reading text", "Texte de lecture")),
      p(
        cls := "in-english",
        Tr(
          "Written by the tutor with what you have learned, checked by the code. Tap a word for its meaning.",
          "Écrit par le tuteur avec ce que tu as appris, vérifié par le code. Touche un mot pour voir son sens."
        )
      ),
      asked.events.mapTo(true) --> writing.writer,
      text.changes.collect { case Some(_) => false } --> writing.writer,
      child <-- text.map {
        case Some(Right(Some(written))) =>
          div(
            written.sentences.map(shown =>
              div(
                cls := "lesson-example",
                sentence(shown, selected),
                detailsTag(
                  summaryTag(Tr("Translation", "Traduction")),
                  p(cls := "example-french", shown.french)
                )
              )
            )
          )
        case Some(Right(None)) =>
          p(
            cls := "error",
            Tr(
              "The tutor did not manage to write a text with only what you have learned; try again.",
              "Le tuteur n'a pas réussi à écrire un texte avec seulement ce que tu as appris ; réessaie."
            )
          )
        case Some(Left(reason)) => p(cls := "error", reason)
        case None               => emptyNode
      },
      button(
        cls := "primary",
        disabled <-- writing.signal,
        child.text <-- writing.signal.combineWith(text).map {
          case (true, _)     => Tr("The tutor is writing…", "Le tuteur écrit…")
          case (false, None) => Tr("Write me a text", "Écris-moi un texte")
          case (false, Some(_)) => Tr("Another text", "Un autre texte")
        },
        onClick.mapTo(()) --> asked.writer
      )
    )
  }

  // a word of the unit: its reading, its usual writing, its meaning
  private def unitWord(word: DictionaryWord): HtmlElement = {
    val french = word.meanings.filter(_.language == "fre")
    val meanings =
      if (french.nonEmpty) french else word.meanings.filter(_.language == "eng")
    div(
      cls := "unit-word",
      span(
        cls := "word-reading",
        lang := "ja",
        word.readings.headOption.getOrElse("")
      ),
      Option.when(!word.usuallyKana)(
        word.writings.headOption.map(writing =>
          span(cls := "word-writing", lang := "ja", writing)
        )
      ),
      span(
        cls := "unit-meaning",
        meanings.headOption.fold("")(_.glosses.take(3).mkString(", "))
      )
    )
  }

  private def link(
      point: GrammarPoint,
      before: String = "",
      after: String = ""
  ) = PageLink(bus, Page.Lesson(point.id), before, point.title, after)

  private def view: HtmlElement = {
    val selected = Var(Option.empty[PartWord])
    div(
      div(
        cls := "lesson-heading",
        h1(lesson.point.title),
        span(cls := "word-level", s"N${lesson.point.level}")
      ),
      p(cls := "grammar-pattern", lang := "ja", lesson.point.pattern),
      Option.when(lesson.requires.nonEmpty)(
        p(
          cls := "lesson-requires",
          Tr("Builds on: ", "S'appuie sur : "),
          lesson.requires
            .flatMap(point => List[Modifier[HtmlElement]](link(point), " · "))
            .dropRight(1)
        )
      ),
      div(cls := "lesson-explanation", lesson.explanation.map(paragraph)),
      Option.when(lesson.vocabulary.nonEmpty)(
        div(
          cls := "lesson-vocabulary",
          h2(Tr("Vocabulary: ", "Vocabulaire : "), lesson.theme),
          div(cls := "unit-words", lesson.vocabulary.map(unitWord))
        )
      ),
      h2(Tr("Examples", "Exemples")),
      lesson.examples.map(example(_, selected)),
      Option.when(lesson.hasText)(readingText(lesson.point.id, selected)),
      LessonExercises(bus, lesson.point.id, onExercisesDone),
      wordShown(selected),
      navTag(
        cls := "lesson-nav",
        lesson.previous.map(link(_, before = "← ")),
        lesson.next.map(link(_, after = " →"))
      )
    )
  }

  override lazy val node: HtmlElement = view
}
