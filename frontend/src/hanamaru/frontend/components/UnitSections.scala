package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.characters.CharactersOf
import hanamaru.shared.kana.Script
import hanamaru.shared.lessons.*
import hanamaru.shared.vocabulary.SeriesOf

import com.raquo.laminar.api.L.*

/** A unit's sections, side by side: its grammar (the start of its lesson, read
  * again whole or practised), its symbols (the kana chart of its script for the
  * first two units, else its kanji, each where it stands), and its words, each
  * with a short meaning, its senses unfolded on demand. Each practice opens in
  * a pop-in; once it is closed, `closed` is called, for the unit to be asked
  * again.
  */
class UnitSections(bus: AppBus, recap: UnitRecap, closed: () => Unit)
    extends ReactiveElement {

  private enum Practice {
    case Lesson, Exercises, Words, Kanji
    case Write(literal: String)
    case Focus(literal: String)
  }

  private val opened = Var(Option.empty[Practice])

  private def close(): Unit = {
    opened.set(None)
    closed()
  }

  private def opener(label: String, practice: Practice): HtmlElement = button(
    label,
    onClick.mapTo(Some(practice)) --> opened.writer
  )

  private def headingOf(practice: Practice): String = practice match {
    case Practice.Lesson    => recap.point.title
    case Practice.Exercises => Tr("Exercises", "Exercices")
    case Practice.Words     => Tr("The unit's words", "Les mots de l'unité")
    case Practice.Kanji     => Tr("The unit's kanji", "Les kanji de l'unité")
    case Practice.Write(literal) => literal
    case Practice.Focus(literal) =>
      Tr(s"Focus on $literal", s"Focus sur $literal")
  }

  private def contentOf(practice: Practice): HtmlElement = practice match {
    case Practice.Lesson =>
      div(
        child <-- bus
          .request(AppCommand.LoadLesson(recap.point.id)) {
            case AppEvent.LessonLoaded(lesson)
                if lesson.point.id == recap.point.id =>
              lesson
          }
          .map {
            case Right(lesson) => LessonView(bus, lesson).node
            case Left(reason)  => p(cls := "error", reason)
          }
      )
    case Practice.Exercises =>
      LessonExercises(bus, ExercisesOf.Lesson(recap.point.id)).node
    case Practice.Words =>
      VocabularySeries(
        bus,
        EventStream.fromValue(()),
        SeriesOf.OfUnit(recap.point.id),
        Some(() => close())
      ).node
    case Practice.Kanji =>
      CharacterPractice(
        bus,
        () => close(),
        CharactersOf.OfUnit(recap.point.id)
      ).node
    case Practice.Write(literal) => WritingPractice(bus, literal).node
    case Practice.Focus(literal) =>
      FocusView(bus, literal, () => close()).node
  }

  private def grammarSection: HtmlElement = sectionTag(
    cls := "unit-card",
    h2(Tr("Grammar", "Grammaire")),
    p(cls := "unit-point-title", recap.point.title),
    LessonView.paragraph(recap.introduction),
    div(
      cls := "unit-actions",
      opener(Tr("Read the lesson", "Relire la leçon"), Practice.Lesson)
        .amend(cls := "primary"),
      opener(Tr("Exercises", "Exercices"), Practice.Exercises)
    )
  )

  private def wordStanding(standing: WordStanding): HtmlElement = {
    val (kind, said) = standing match {
      case WordStanding.New      => ("is-new", Tr("new", "nouveau"))
      case WordStanding.Learning =>
        ("is-learning", Tr("being learned", "en cours"))
      case WordStanding.Acquired => ("is-acquired", Tr("acquired", "acquis"))
    }
    span(cls := s"standing-dot $kind", title := said, aria.label := said)
  }

  private def wordRow(each: RecapWord): HtmlElement = {
    val unfolded = Var(false)
    div(
      cls := "unit-word-row",
      div(
        cls := "unit-word-line",
        wordStanding(each.standing),
        span(
          cls := "word-writing",
          lang := "ja",
          each.word.writings.headOption
            .filter(_ => !each.word.usuallyKana)
            .orElse(each.word.readings.headOption)
            .getOrElse("")
        ),
        span(
          cls := "word-reading",
          lang := "ja",
          Option
            .when(!each.word.usuallyKana && each.word.writings.nonEmpty)(
              each.word.readings.headOption
            )
            .flatten
            .getOrElse("")
        ),
        span(cls := "word-short", each.meaning.short),
        Option.when(each.meaning.senses.nonEmpty)(
          button(
            cls := "icon-button",
            aria.label := Tr("Every sense", "Tous les sens"),
            aria.expanded <-- unfolded.signal,
            child <-- unfolded.signal.map(unfolded =>
              ButtonIcon(
                if (unfolded) ButtonIcon.Shape.Minus else ButtonIcon.Shape.Plus
              ).node
            ),
            onClick --> (_ => unfolded.update(!_))
          )
        )
      ),
      child.maybe <-- unfolded.signal.map(
        Option.when(_)(
          ol(cls := "word-senses", each.meaning.senses.map(li(_)))
        )
      )
    )
  }

  private def wordsSection: HtmlElement = sectionTag(
    cls := "unit-card",
    div(
      cls := "unit-card-head",
      h2(Tr("Vocabulary", "Vocabulaire")),
      opener(
        Tr("A series on these words", "Série sur ces mots"),
        Practice.Words
      )
        .amend(cls := "primary")
    ),
    div(cls := "unit-word-list", recap.words.map(wordRow))
  )

  private def kanaSection(script: Script): HtmlElement = sectionTag(
    cls := "unit-card",
    h2(script match {
      case Script.Hiragana => Tr("Its hiragana", "Ses hiragana")
      case Script.Katakana => Tr("Its katakana", "Ses katakana")
    }),
    KanaChart(bus, Some(script)).node,
    PageLink(
      bus,
      Page.Session,
      cls := "button primary",
      Tr("An Express session on the kana", "Une séance express sur les kana")
    )
  )

  private def kanjiStanding(standing: KanjiStanding): HtmlElement =
    standing match {
      case KanjiStanding.NotMet =>
        span(cls := "standing is-new", Tr("not met yet", "pas encore vu"))
      case KanjiStanding.Reading =>
        span(cls := "standing is-learning", Tr("being read", "en lecture"))
      case KanjiStanding.Read =>
        span(cls := "standing is-learning", Tr("read", "lu"))
      case KanjiStanding.Written =>
        span(
          cls := "standing is-acquired",
          Tr("read and written", "lu et écrit")
        )
    }

  private def kanjiCard(each: RecapKanji): HtmlElement = div(
    cls := "unit-kanji-card",
    kanjiStanding(each.standing),
    PageLink(
      bus,
      Page.KanjiCharacter(each.kanji.literal),
      cls := "unit-kanji-literal",
      lang := "ja",
      each.kanji.literal
    ),
    span(
      cls := "unit-kanji-meaning",
      each.kanji.meanings.take(2).mkString(", ")
    ),
    span(
      lang := "ja",
      cls := "unit-kanji-readings",
      (each.kanji.on.take(2) ++ each.kanji.kun.take(2)).mkString("、")
    ),
    // not met yet: learned in a focus, ahead of My lesson
    if (each.standing == KanjiStanding.NotMet)
      opener(Tr("Focus", "Focus"), Practice.Focus(each.kanji.literal))
        .amend(cls := "primary")
    else
      opener(Tr("Write it", "L'écrire"), Practice.Write(each.kanji.literal))
  )

  private def kanjiSection: HtmlElement = sectionTag(
    cls := "unit-card",
    div(
      cls := "unit-card-head",
      h2(Tr("Kanji", "Kanji")),
      Option.when(recap.kanji.exists(_.standing != KanjiStanding.NotMet))(
        opener(
          Tr("A series on these kanji", "Série sur ces kanji"),
          Practice.Kanji
        ).amend(cls := "primary")
      )
    ),
    if (recap.kanji.isEmpty)
      p(Tr("This unit has no kanji.", "Cette unité n'a pas de kanji."))
    else div(cls := "unit-kanji", recap.kanji.map(kanjiCard)),
    Option.when(
      recap.kanji.nonEmpty &&
        recap.kanji.forall(_.standing == KanjiStanding.NotMet)
    )(
      p(
        cls := "in-english",
        Tr(
          "None met yet: My lesson brings them, each in a focus, or start one here.",
          "Aucun vu encore : Ma leçon les apporte, chacun dans un focus, ou lance-en un ici."
        )
      )
    )
  )

  override lazy val node: HtmlElement = div(
    cls := "unit-sections",
    div(
      cls := "unit-column",
      grammarSection,
      recap.kana.fold(kanjiSection)(kanaSection)
    ),
    div(cls := "unit-column", wordsSection),
    child.maybe <-- opened.signal.map(
      _.map(practice =>
        Modal(headingOf(practice), contentOf(practice), () => close()).node
      )
    )
  )
}
