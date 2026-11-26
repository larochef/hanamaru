package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.characters.CharactersOf
import hanamaru.shared.kana.Script
import hanamaru.shared.lessons.*
import hanamaru.shared.vocabulary.SeriesOf

import com.raquo.laminar.api.L.*

/** A unit gathered to be gone over, as the server found it: its lesson, read
  * again and practised; its words, each where it stands, practised in a series
  * of their own; its symbols, the kana chart of its script for the first two
  * units, else its kanji, each where it stands, written freely or practised in
  * a series of their own. Each practice opens in the page, and the unit is
  * asked again once it is closed. The units so far are a click away.
  */
class UnitPage(bus: AppBus) extends ReactiveElement {

  private enum Panel {
    case Lesson, Words, Kanji
    case Write(literal: String)
  }

  private val shown: Signal[Option[String]] = bus.events.events
    .collect {
      case AppEvent.PageShown(Page.Unit(point)) => Some(point)
      case AppEvent.PageShown(_)                => None
    }
    .startWith(None)

  private val panel = Var(Option.empty[Panel])
  private val closed = EventBus[Unit]()

  private def close(): Unit = {
    panel.set(None)
    closed.writer.onNext(())
  }

  private val recap: Signal[Option[AppResult[UnitRecap]]] = EventStream
    .merge(shown.changes, closed.events.sample(shown))
    .flatMapSwitch {
      case Some(point) =>
        bus
          .request(AppCommand.LoadUnitRecap(point)) {
            case AppEvent.UnitRecapLoaded(recap) if recap.point.id == point =>
              recap
          }
          .map(Some(_))
      case None => EventStream.fromValue(None)
    }
    .startWith(None)

  private def opener(label: String, opened: Panel): HtmlElement = button(
    cls := "primary",
    label,
    onClick.mapTo(Some(opened)) --> panel.writer
  )

  private def closer: HtmlElement = button(
    Tr("Close", "Fermer"),
    onClick --> (_ => close())
  )

  // what is opened, in the page, under its section
  private def opened(recap: UnitRecap, at: Panel): HtmlElement = div(
    cls := "unit-panel",
    at match {
      case Panel.Lesson =>
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
      case Panel.Words =>
        VocabularySeries(
          bus,
          EventStream.fromValue(()),
          SeriesOf.OfUnit(recap.point.id),
          Some(() => close())
        ).node
      case Panel.Kanji =>
        CharacterPractice(
          bus,
          () => close(),
          CharactersOf.OfUnit(recap.point.id)
        ).node
      case Panel.Write(literal) => WritingPractice(bus, literal).node
    },
    closer
  )

  private def panelIn(recap: UnitRecap, of: Panel => Boolean) =
    child.maybe <-- panel.signal.map(_.filter(of).map(opened(recap, _)))

  private def wordStanding(standing: WordStanding): HtmlElement =
    standing match {
      case WordStanding.New =>
        span(cls := "standing is-new", Tr("new", "nouveau"))
      case WordStanding.Learning =>
        span(cls := "standing is-learning", Tr("being learned", "en cours"))
      case WordStanding.Acquired =>
        span(cls := "standing is-acquired", Tr("acquired", "acquis"))
    }

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

  private def lessonSection(recap: UnitRecap): HtmlElement = sectionTag(
    cls := "unit-section",
    h2(Tr("The lesson", "La leçon")),
    p(span(lang := "ja", recap.point.pattern), " · ", recap.point.title),
    opener(
      Tr(
        "Read the lesson again and do its exercises",
        "Relire la leçon et faire ses exercices"
      ),
      Panel.Lesson
    ),
    panelIn(recap, _ == Panel.Lesson)
  )

  private def wordsSection(recap: UnitRecap): HtmlElement = sectionTag(
    cls := "unit-section",
    h2(Tr("The words", "Les mots")),
    table(
      cls := "unit-words",
      tbody(
        recap.words.map(each =>
          tr(
            td(
              lang := "ja",
              cls := "word-writing",
              each.word.writings.headOption
                .orElse(each.word.readings.headOption)
                .getOrElse("")
            ),
            td(
              lang := "ja",
              cls := "word-reading",
              Option
                .when(each.word.writings.nonEmpty)(
                  each.word.readings.headOption
                )
                .flatten
                .getOrElse("")
            ),
            td(each.word.firstMeaning),
            td(wordStanding(each.standing))
          )
        )
      )
    ),
    opener(
      Tr("A series on these words", "Une série sur ces mots"),
      Panel.Words
    ),
    panelIn(recap, _ == Panel.Words)
  )

  private def kanaSection(script: Script): HtmlElement = sectionTag(
    cls := "unit-section",
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

  private def kanjiSection(recap: UnitRecap): HtmlElement = sectionTag(
    cls := "unit-section",
    h2(Tr("Its kanji", "Ses kanji")),
    if (recap.kanji.isEmpty)
      p(Tr("This unit has no kanji.", "Cette unité n'a pas de kanji."))
    else
      div(
        div(
          cls := "unit-kanji",
          recap.kanji.map(each =>
            div(
              cls := "unit-kanji-card",
              PageLink(
                bus,
                Page.KanjiCharacter(each.kanji.literal),
                cls := "unit-kanji-literal",
                lang := "ja",
                each.kanji.literal
              ),
              span(each.kanji.meanings.take(2).mkString(", ")),
              span(
                lang := "ja",
                cls := "unit-kanji-readings",
                (each.kanji.on.take(2) ++ each.kanji.kun.take(2))
                  .mkString("、")
              ),
              kanjiStanding(each.standing),
              button(
                Tr("Write it", "L'écrire"),
                onClick.mapTo(Some(Panel.Write(each.kanji.literal))) -->
                  panel.writer
              )
            )
          )
        ),
        panelIn(recap, _.isInstanceOf[Panel.Write]),
        if (recap.kanji.exists(_.standing != KanjiStanding.NotMet))
          opener(
            Tr("A series on these kanji", "Une série sur ces kanji"),
            Panel.Kanji
          )
        else
          p(
            cls := "in-english",
            Tr(
              "None met yet: My lesson brings them, each in a word you know.",
              "Aucun vu encore : Ma leçon les apporte, chacun dans un mot que tu connais."
            )
          ),
        panelIn(recap, _ == Panel.Kanji)
      )
  )

  private def unitsNav(recap: UnitRecap): HtmlElement = div(
    cls := "unit-nav",
    recap.units.zipWithIndex.map((point, index) =>
      PageLink(
        bus,
        Page.Unit(point.id),
        cls := "unit-nav-link",
        cls("is-selected") := point.id == recap.point.id,
        title := point.title,
        (index + 1).toString
      ).node
    )
  )

  private def page(recap: UnitRecap): HtmlElement = {
    val index = recap.units.indexWhere(_.id == recap.point.id)
    div(
      unitsNav(recap),
      h1(
        Tr(s"Unit ${index + 1}: ", s"Unité ${index + 1} : "),
        recap.theme
      ),
      Option.when(recap.acquired)(
        p(cls := "unit-next", Tr("Acquired.", "Acquise."))
      ),
      lessonSection(recap),
      wordsSection(recap),
      recap.kana.fold(kanjiSection(recap))(kanaSection)
    )
  }

  override lazy val node: HtmlElement = div(
    cls := "unit-page",
    shown.changes --> (_ => panel.set(None)),
    PageLink(
      bus,
      Page.LessonSession,
      cls := "back",
      Tr("← My lesson", "← Ma leçon")
    ),
    child <-- recap.map {
      case Some(Right(recap)) => page(recap)
      case Some(Left(reason)) => p(cls := "error", reason)
      case None => p(cls := "in-english", Tr("Loading…", "Chargement…"))
    }
  )
}
