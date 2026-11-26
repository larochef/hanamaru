package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.characters.{CharacterAsked, KanaReadingAnswer}
import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.kana.Kana
import hanamaru.shared.placement.ReadingResult
import hanamaru.shared.writing.WritingStep

import com.raquo.laminar.api.L.*

/** A kana exercise: read (shown, its reading typed in romaji; a katakana in a
  * loanword, the word's reading typed in kana), or written from its reading; a
  * new kana is shown stroke by stroke first, then written over its model, then
  * from memory. Each answer is kept as a review by the server. Once done,
  * `next` leads on.
  */
class KanaExerciseView(
    bus: AppBus,
    kana: Kana,
    asked: CharacterAsked,
    isNew: Boolean,
    inWord: Option[DictionaryWord],
    next: () => Unit
) extends ReactiveElement {

  private enum Stage {
    case Shown, Guided, FromMemory, Read, Forgotten
    case Judged(result: ReadingResult)
  }

  private val stage = Var(
    if (asked == CharacterAsked.Reading) Stage.Read
    else if (isNew) Stage.Shown
    else Stage.FromMemory
  )

  private def continueButton(to: () => Unit): HtmlElement = button(
    cls := "primary",
    Tr("Continue", "Continuer"),
    onMountFocus,
    onClick --> (_ => to())
  )

  private def heading: HtmlElement = div(
    cls := "character-heading",
    span(cls := "character-model", lang := "ja", kana.character),
    div(
      KanaReading(kana, "character-reading"),
      span(cls := "character-script", Tr("new kana", "nouveau kana"))
    )
  )

  private def shown: HtmlElement = div(
    heading,
    WritingPractice(bus, kana.character).node,
    continueButton(() => stage.set(Stage.Guided))
  )

  private def guided: HtmlElement = div(
    heading,
    p(
      cls := "placement-prompt",
      Tr("Write it over its model.", "Écris-le sur son modèle.")
    ),
    StrokesOf(
      bus,
      kana.character,
      strokes => {
        val writing =
          WritingExercise(strokes, WritingStep.Guided, canStartAgain = true)
        writing.node.amend(
          child.maybe <-- writing.attempts
            .filter(_.completed)
            .mapTo(Some(continueButton(() => stage.set(Stage.FromMemory))))
        )
      }
    )
  )

  // written, it is corrected in place, and kept as a review
  private def fromMemory: HtmlElement = div(
    p(
      cls := "placement-prompt",
      Tr(
        s"Write from memory, in ${KanaReading.scriptName(kana.script)}:",
        s"Écris de mémoire, en ${KanaReading.scriptName(kana.script)} :"
      )
    ),
    KanaReading(kana, "placement-reading"),
    StrokesOf(
      bus,
      kana.character,
      strokes => {
        val writing = WritingExercise(
          strokes,
          WritingStep.FromMemory,
          canStartAgain = false,
          canForget = true
        )
        writing.node.amend(
          writing.attempts --> { attempt =>
            bus.send(AppCommand.RecordWriting(attempt))
            if (attempt.forgotten) stage.set(Stage.Forgotten)
          },
          child.maybe <-- writing.attempts
            .filterNot(_.forgotten)
            .mapTo(Some(continueButton(next)))
        )
      }
    )
  )

  private def judgedBy(answers: EventStream[KanaReadingAnswer]) =
    answers.flatMapSwitch(answer =>
      bus.request(AppCommand.AnswerKanaReading(answer)) {
        case AppEvent.KanaReadingJudged(`answer`, result) => result
      }
    ) --> {
      case Right(result) => stage.set(Stage.Judged(result))
      case Left(_)       => ()
    }

  private def read: HtmlElement = inWord.fold {
    val question = ReadingQuestion(kana)
    question.node.amend(
      judgedBy(
        question.answers.map(reading =>
          KanaReadingAnswer(kana.character, reading.typed)
        )
      )
    )
  } { word =>
    // typed in romaji, shown in katakana as it is typed
    val field = KanaInput(katakana = true)
    val stylusField = StylusField(bus, field, Var(true))
    val answers = EventBus[KanaReadingAnswer]()
    def answer(typed: Option[String]) =
      KanaReadingAnswer(kana.character, typed, Some(word.id))
    div(
      p(
        cls := "placement-prompt",
        Tr("How is this word read?", "Comment se lit ce mot ?")
      ),
      span(
        cls := "word-big",
        lang := "ja",
        word.readings.headOption.getOrElse("")
      ),
      form(
        onSubmit.preventDefault.map { _ =>
          stylusField.keep(field.value)
          answer(Some(field.value))
        } --> answers.writer,
        field,
        button(typ := "submit", cls := "primary", Tr("Answer", "Valider"))
      ),
      stylusField.node,
      button(
        Tr("I don't know", "Je ne sais pas"),
        onClick.mapTo(answer(None)) --> answers.writer
      ),
      judgedBy(answers.events)
    )
  }

  private def judged(result: ReadingResult): HtmlElement = div(
    span(
      cls := "character-model",
      lang := "ja",
      inWord.flatMap(_.readings.headOption).getOrElse(kana.character)
    ),
    p(
      cls := (if (result.correct) "verdict is-right" else "verdict is-wrong"),
      if (result.correct) Tr("Right!", "Juste !")
      else Tr("It reads ", "Il se lit ") + result.romaji
    ),
    inWord.map(word => p(cls := "series-meaning", word.firstMeaning)),
    continueButton(next)
  )

  override lazy val node: HtmlElement = div(
    cls := "kana-exercise",
    child <-- stage.signal.map {
      case Stage.Shown          => shown
      case Stage.Guided         => guided
      case Stage.FromMemory     => fromMemory
      case Stage.Read           => read
      case Stage.Judged(result) => judged(result)
      // not remembered: shown and written again, its review kept as missed
      case Stage.Forgotten => Relearn(bus, kana.character, next).node
    }
  )
}
