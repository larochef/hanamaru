package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.characters.*
import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.kanji.*
import hanamaru.shared.questions.{AskedFrom, QuestionContext}
import hanamaru.shared.writing.WritingStep

import com.raquo.laminar.api.L.*

/** A kanji exercise, in a word the learner knows: a new kanji shown first (its
  * meanings, readings and strokes), then read in its word, its reading typed; a
  * kanji to write, written over its model the first time, then from memory from
  * the word's reading and meaning. Once done, `next` leads on.
  */
class KanjiExerciseView(
    bus: AppBus,
    exercise: KanjiExercise,
    next: () => Unit,
    kept: AnswerKept = AnswerKept.AsReview,
    answered: CardResult => Unit = _ => ()
) extends ReactiveElement {

  private enum Stage {
    case Shown, Asked, Guided, FromMemory, Forgotten
    case Judged(answer: KanjiReadingAnswer, verdict: KanjiReadingVerdict)
  }

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
    span(cls := "word-writing", lang := "ja", exercise.shown),
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
      WritingPractice(bus, exercise.kanji.literal).node,
      continueButton(next)
    )

  private def asked(
      judged: (KanjiReadingAnswer, KanjiReadingVerdict) => Unit
  ): HtmlElement = {
    val mode = Var(KanaInput.Mode.Hiragana)
    val stylus = Var(true)
    val field = KanaInput(mode, stylus = stylus.signal)
    val panel = AnswerPanel(
      bus,
      () => field,
      stylus,
      mode,
      Set(KanaInput.Mode.Hiragana, KanaInput.Mode.Katakana),
      field.node
    )
    val submitted = EventBus[KanjiReadingAnswer]()
    def dontKnow(): Unit = submitted.writer.onNext(
      KanjiReadingAnswer(exercise.kanji.literal, exercise.word.id, "", kept)
    )
    div(
      cls := "series-task",
      p(cls := "placement-prompt", Tr("How is it read?", "Comment se lit :")),
      // its meaning only once answered: given, it gives the word away
      span(cls := "word-big", lang := "ja", exercise.shown),
      form(
        cls := "written-answer",
        onSubmit.preventDefault.map { _ =>
          panel.keep(field.value)
          KanjiReadingAnswer(
            exercise.kanji.literal,
            exercise.word.id,
            field.value,
            kept
          )
        } --> submitted.writer,
        panel.node,
        AnswerActions(() => dontKnow()).node
      ),
      submitted.events.flatMapSwitch(answer =>
        bus
          .request(AppCommand.AnswerKanjiReading(answer)) {
            case AppEvent.KanjiReadingJudged(`answer`, verdict) => verdict
          }
          .map(answer -> _)
      ) --> {
        case (answer, Right(verdict)) =>
          answered(
            if (verdict.right || verdict.kanjiRead.nonEmpty) CardResult.Right
            else CardResult.Missed
          )
          judged(answer, verdict)
        case (_, Left(_)) => ()
      },
      child.maybe <-- bus.events.events.collect {
        case AppEvent.RequestFailed(AppCommand.AnswerKanjiReading(_), reason) =>
          Some(p(cls := "is-failed", reason))
      }
    )
  }

  private def judgedView(
      answer: KanjiReadingAnswer,
      verdict: KanjiReadingVerdict,
      next: () => Unit
  ): HtmlElement = div(
    cls := "series-task",
    verdict.kanjiRead.fold(
      p(
        cls := (if (verdict.right) "verdict is-right" else "verdict is-wrong"),
        if (verdict.right) Tr("Right!", "Juste !")
        else
          Tr("Not quite: it reads ", "Pas tout à fait : il se lit ") +
            verdict.reading
      )
    )(part =>
      p(
        cls := "verdict is-near",
        Tr(
          s"${exercise.kanji.literal} is read right ($part); the word reads ${verdict.reading}.",
          s"${exercise.kanji.literal} est bien lu ($part) ; le mot se lit ${verdict.reading}."
        )
      )
    ),
    Option.when(!verdict.right && answer.typed.nonEmpty)(
      YouWrote(answer.typed).node
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
      exercise.shown.map(character =>
        if (character.toString == exercise.kanji.literal) '□' else character
      )
    ),
    span(cls := "word-reading", lang := "ja", readingOf(exercise.word)),
    div(cls := "series-meaning", exercise.word.firstMeaning)
  )

  // its writing new: shown as it is written, practised freely, then written
  // from memory
  private def guided(next: () => Unit): HtmlElement =
    div(
      withBlank,
      p(
        cls := "placement-prompt",
        Tr(
          "How it is written: practise it, then write it from memory.",
          "Comment il s'écrit : entraîne-toi, puis écris-le de mémoire."
        )
      ),
      WritingPractice(bus, exercise.kanji.literal).node,
      continueButton(next)
    )

  // written, it is corrected in place, and the word shown whole; not
  // remembered, `forgot`
  private def fromMemory(next: () => Unit, forgot: () => Unit): HtmlElement =
    div(
      withBlank,
      StrokesOf(
        bus,
        exercise.kanji.literal,
        strokes => {
          val writing = WritingExercise(
            strokes,
            WritingStep.FromMemory,
            canStartAgain = false,
            canForget = true
          )
          writing.node.amend(
            writing.attempts --> { attempt =>
              bus.send(
                if (kept == AnswerKept.InFocus) AppCommand.KeepWriting(attempt)
                else AppCommand.RecordKanjiWriting(attempt)
              )
              answered(CardResult.of(attempt))
              if (attempt.forgotten) forgot()
            },
            child.maybe <-- writing.attempts
              .filterNot(_.forgotten)
              .mapTo(Some(div(wordShown(exercise.word), continueButton(next))))
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
    // the kanji under way, as the tutor is told it
    val about = QuestionContext(
      AskedFrom.Exercise,
      // as the dictionary writes it, for the server to find it there
      word = exercise.word.writings.headOption,
      task =
        Some(
          if (reading)
            s"Read the word ${exercise.shown}, for its kanji ${exercise.kanji.literal} (expected: ${readingOf(exercise.word)})"
          else
            s"Write the kanji ${exercise.kanji.literal} of the word ${exercise.shown} (${readingOf(exercise.word)})"
        )
    )
    div(
      cls := "kanji-exercise",
      TutorContext(
        bus,
        stage.signal.map {
          case Stage.Judged(answer, verdict) =>
            Some(
              about.copy(
                from = AskedFrom.Correction,
                answer = Some(answer.typed).filter(_.nonEmpty),
                verdict = Some(
                  if (verdict.right) "right"
                  else
                    verdict.kanjiRead.fold(
                      s"wrong: it reads ${verdict.reading}"
                    )(part =>
                      s"the kanji read right ($part), the word reads ${verdict.reading}"
                    )
                )
              )
            )
          case _ => Some(about)
        }
      ).node,
      child <-- stage.signal.map {
        case Stage.Shown => shown(() => stage.set(Stage.Asked))
        case Stage.Asked =>
          asked((answer, verdict) => stage.set(Stage.Judged(answer, verdict)))
        case Stage.Judged(answer, verdict) => judgedView(answer, verdict, next)
        case Stage.Guided                  =>
          guided(() => stage.set(Stage.FromMemory))
        case Stage.FromMemory =>
          fromMemory(next, () => stage.set(Stage.Forgotten))
        // not remembered: shown and written again, its review kept as missed
        case Stage.Forgotten =>
          div(
            wordShown(exercise.word),
            Relearn(bus, exercise.kanji.literal, next).node
          )
      }
    )
  }
}
