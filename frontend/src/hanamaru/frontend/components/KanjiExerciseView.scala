package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.characters.CharacterAsked
import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.kanji.*
import hanamaru.shared.writing.WritingStep

import com.raquo.laminar.api.L.*

/** A kanji exercise, in a word the learner knows: a new kanji shown first (its
  * meanings, readings and strokes), then read in its word, its reading typed; a
  * kanji to write, written over its model the first time, then from memory from
  * the word's reading and meaning. Once done, `next` leads on.
  */
class KanjiExerciseView(bus: AppBus, exercise: KanjiExercise, next: () => Unit)
    extends ReactiveElement {

  private enum Stage {
    case Shown, Asked, Guided, FromMemory
    case Judged(verdict: KanjiReadingVerdict)
  }

  private def writingOf(word: DictionaryWord): String =
    word.writings.headOption.getOrElse("")

  private def readingOf(word: DictionaryWord): String =
    word.readings.headOption.getOrElse("")

  private def continueButton(next: () => Unit): HtmlElement = button(
    cls := "primary",
    Tr("Continue", "Continuer"),
    onMountFocus,
    onClick --> (_ => next())
  )

  private def heading(kanji: Kanji): HtmlElement = div(
    cls := "kanji-heading",
    span(cls := "kanji-model", lang := "ja", kanji.literal),
    div(
      div(cls := "kanji-meanings", kanji.meanings.take(5).mkString(", ")),
      Option.when(kanji.on.nonEmpty)(
        p(
          Tr("On readings: ", "Lectures on : "),
          span(lang := "ja", cls := "kanji-readings", kanji.on.mkString("・"))
        )
      ),
      Option.when(kanji.kun.nonEmpty)(
        p(
          Tr("Kun readings: ", "Lectures kun : "),
          span(lang := "ja", cls := "kanji-readings", kanji.kun.mkString("・"))
        )
      )
    )
  )

  // the word, whole: as written, its reading and its meaning
  private def wordShown(word: DictionaryWord): HtmlElement = div(
    cls := "unit-word",
    span(cls := "word-writing", lang := "ja", writingOf(word)),
    span(cls := "word-reading", lang := "ja", readingOf(word)),
    span(cls := "unit-meaning", word.firstMeaning)
  )

  private def shown(next: () => Unit): HtmlElement =
    div(
      p(
        cls := "placement-prompt",
        Tr(
          "A new kanji, in a word you know:",
          "Un nouveau kanji, dans un mot que tu connais :"
        )
      ),
      heading(exercise.kanji),
      wordShown(exercise.word),
      StrokesOf(bus, exercise.kanji.literal, StrokeDemonstration(_).node),
      continueButton(next)
    )

  private def asked(judged: KanjiReadingVerdict => Unit): HtmlElement = {
    val field = KanaInput()
    val submitted = EventBus[KanjiReadingAnswer]()
    div(
      cls := "series-task",
      p(cls := "placement-prompt", Tr("How is it read?", "Comment se lit :")),
      span(cls := "word-big", lang := "ja", writingOf(exercise.word)),
      div(cls := "series-meaning", exercise.word.firstMeaning),
      form(
        onSubmit.preventDefault.mapTo(
          KanjiReadingAnswer(
            exercise.kanji.literal,
            exercise.word.id,
            field.value
          )
        ) --> submitted.writer,
        field,
        button(typ := "submit", cls := "primary", Tr("Answer", "Valider"))
      ),
      button(
        Tr("I don't know", "Je ne sais pas"),
        onClick.mapTo(
          KanjiReadingAnswer(exercise.kanji.literal, exercise.word.id, "")
        ) --> submitted.writer
      ),
      submitted.events.flatMapSwitch(answer =>
        bus.request(AppCommand.AnswerKanjiReading(answer)) {
          case AppEvent.KanjiReadingJudged(`answer`, verdict) => verdict
        }
      ) --> {
        case Right(verdict) => judged(verdict)
        case Left(_)        => ()
      },
      child.maybe <-- bus.events.events.collect {
        case AppEvent.RequestFailed(AppCommand.AnswerKanjiReading(_), reason) =>
          Some(p(cls := "is-failed", reason))
      }
    )
  }

  private def judgedView(
      verdict: KanjiReadingVerdict,
      next: () => Unit
  ): HtmlElement = div(
    cls := "series-task",
    p(
      cls := (if (verdict.right) "verdict is-right" else "verdict is-wrong"),
      if (verdict.right) Tr("Right!", "Juste !")
      else
        Tr("Not quite: it reads ", "Pas tout à fait : il se lit ") +
          verdict.reading
    ),
    wordShown(exercise.word),
    continueButton(next)
  )

  // the word with its kanji left blank, to be written
  private def withBlank: HtmlElement = div(
    cls := "series-task",
    p(cls := "placement-prompt", Tr("Write its kanji:", "Écris son kanji :")),
    span(
      cls := "word-big",
      lang := "ja",
      writingOf(exercise.word).map(character =>
        if (character.toString == exercise.kanji.literal) '□' else character
      )
    ),
    span(cls := "word-reading", lang := "ja", readingOf(exercise.word)),
    div(cls := "series-meaning", exercise.word.firstMeaning)
  )

  private def guided(next: () => Unit): HtmlElement =
    div(
      withBlank,
      p(
        cls := "placement-prompt",
        Tr("First over its model:", "D'abord sur son modèle :")
      ),
      StrokesOf(
        bus,
        exercise.kanji.literal,
        strokes => {
          val writing =
            WritingExercise(strokes, WritingStep.Guided, canStartAgain = true)
          writing.node.amend(
            child.maybe <-- writing.attempts
              .filter(_.completed)
              .map(_ => Some(continueButton(next)))
          )
        }
      )
    )

  // written, it is corrected in place, and the word shown whole
  private def fromMemory(next: () => Unit): HtmlElement =
    div(
      withBlank,
      StrokesOf(
        bus,
        exercise.kanji.literal,
        strokes => {
          val writing = WritingExercise(
            strokes,
            WritingStep.FromMemory,
            canStartAgain = false
          )
          writing.node.amend(
            writing.attempts --> (attempt =>
              bus.send(AppCommand.RecordKanjiWriting(attempt))
            ),
            child.maybe <-- writing.attempts.mapTo(
              Some(div(wordShown(exercise.word), continueButton(next)))
            )
          )
        }
      )
    )

  override lazy val node: HtmlElement = {
    val reading = exercise.asked == CharacterAsked.Reading
    val stage = Var(
      if (exercise.isNew && reading) Stage.Shown
      else if (exercise.isNew) Stage.Guided
      else if (reading) Stage.Asked
      else Stage.FromMemory
    )
    div(
      cls := "kanji-exercise",
      child <-- stage.signal.map {
        case Stage.Shown => shown(() => stage.set(Stage.Asked))
        case Stage.Asked =>
          asked(verdict => stage.set(Stage.Judged(verdict)))
        case Stage.Judged(verdict) => judgedView(verdict, next)
        case Stage.Guided          =>
          guided(() => stage.set(Stage.FromMemory))
        case Stage.FromMemory => fromMemory(next)
      }
    )
  }
}
