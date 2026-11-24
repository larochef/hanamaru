package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.kanji.*
import hanamaru.shared.writing.WritingStep

import com.raquo.laminar.api.L.*

/** A lesson session's kanji, one exercise after the other, each in a word the
  * learner knows: a new kanji shown first (its meanings, readings and strokes),
  * then read in its word, its reading typed; a kanji to write, written over its
  * model the first time, then from memory from the word's reading and meaning.
  * Once all are done, `goOn` leads on.
  */
class KanjiPractice(bus: AppBus, goOn: () => Unit) extends ReactiveElement {

  private enum Stage {
    case Shown, Asked, Guided, FromMemory
    case Judged(verdict: KanjiReadingVerdict)
  }

  private val at = Var(0)

  private val exercises: EventStream[AppResult[List[KanjiExercise]]] =
    bus.request(AppCommand.LoadKanjiPractice) {
      case AppEvent.KanjiPracticeLoaded(exercises) => exercises
    }

  private def meaningOf(word: DictionaryWord): String = {
    val french = word.meanings.filter(_.language == "fre")
    val meanings =
      if (french.nonEmpty) french else word.meanings.filter(_.language == "eng")
    meanings.headOption.fold("")(_.glosses.take(3).mkString(", "))
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
    span(cls := "unit-meaning", meaningOf(word))
  )

  private def shown(exercise: KanjiExercise, next: () => Unit): HtmlElement =
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

  private def asked(
      exercise: KanjiExercise,
      judged: KanjiReadingVerdict => Unit
  ): HtmlElement = {
    val field = KanaInput()
    val submitted = EventBus[KanjiReadingAnswer]()
    div(
      cls := "series-task",
      p(cls := "placement-prompt", Tr("How is it read?", "Comment se lit :")),
      span(cls := "word-big", lang := "ja", writingOf(exercise.word)),
      div(cls := "series-meaning", meaningOf(exercise.word)),
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
      exercise: KanjiExercise,
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
  private def withBlank(exercise: KanjiExercise): HtmlElement = div(
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
    div(cls := "series-meaning", meaningOf(exercise.word))
  )

  private def guided(exercise: KanjiExercise, next: () => Unit): HtmlElement =
    div(
      withBlank(exercise),
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
  private def fromMemory(
      exercise: KanjiExercise,
      next: () => Unit
  ): HtmlElement =
    div(
      withBlank(exercise),
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

  private def exerciseView(
      exercise: KanjiExercise,
      index: Int,
      total: Int
  ): HtmlElement = {
    val reading = exercise.asked == KanjiAsked.Reading
    val stage = Var(
      if (exercise.isNew && reading) Stage.Shown
      else if (exercise.isNew) Stage.Guided
      else if (reading) Stage.Asked
      else Stage.FromMemory
    )
    val next = () => at.update(_ + 1)
    div(
      cls := "kanji-exercise",
      p(
        cls := "in-english",
        Tr(s"Kanji ${index + 1} of $total", s"Kanji ${index + 1} sur $total")
      ),
      child <-- stage.signal.map {
        case Stage.Shown => shown(exercise, () => stage.set(Stage.Asked))
        case Stage.Asked =>
          asked(exercise, verdict => stage.set(Stage.Judged(verdict)))
        case Stage.Judged(verdict) => judgedView(exercise, verdict, next)
        case Stage.Guided          =>
          guided(exercise, () => stage.set(Stage.FromMemory))
        case Stage.FromMemory => fromMemory(exercise, next)
      }
    )
  }

  private def finished: HtmlElement = div(
    cls := "series-task",
    h2(Tr("Kanji done", "Kanji terminés")),
    continueButton(goOn)
  )

  override lazy val node: HtmlElement = div(
    cls := "kanji-practice",
    child <-- exercises.map {
      case Left(reason) => p(cls := "is-failed", reason)
      case Right(all)   =>
        div(
          child <-- at.signal.map(index =>
            all.lift(index).fold(finished)(exerciseView(_, index, all.size))
          )
        )
    }
  )
}
