package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.lessons.*
import hanamaru.shared.text.SentencePart

import com.raquo.laminar.api.L.*

/** A lesson's exercises, one after the other: a particle or a form chosen, a
  * sentence translated from French, one's meaning given in French, a kanji
  * written; each judged by the code, else corrected by the tutor (a meaning,
  * the tutor away, by the learner), then the series recorded as one review of
  * the point (the kanji written are the kanji's reviews); `onDone` offers to go
  * on, in a session.
  */
class LessonExercises(
    bus: AppBus,
    point: String,
    onDone: Option[() => Unit] = None
) extends ReactiveElement {

  private enum Step {
    case Idle
    case Loading
    case Asking(index: Int)
    case Judging(index: Int)
    case Judged(index: Int, answer: String, verdict: ExerciseVerdict)
    case Done(right: Int, total: Int)
    case Failed(reason: String)
  }

  private val step = Var[Step](Step.Idle)
  private val exercises = Var(List.empty[LessonExercise])
  // how each exercise went, sent once all are done: the server counts them
  private val outcomes = Var(List.empty[ExerciseOutcome])
  private val finished = EventBus[ExercisesDone]()
  private val loads = EventBus[Unit]()
  private val answers = EventBus[ExerciseAnswer]()
  // sentences written with the stylus, besides the keyboard
  private val stylus = Var(true)

  private def submit(index: Int, answer: String): Unit = {
    step.set(Step.Judging(index))
    answers.writer.onNext(ExerciseAnswer(exercises.now()(index), answer))
  }

  private def kept(kind: ExerciseKind, right: Boolean): Unit =
    outcomes.update(_ :+ ExerciseOutcome(kind, right))

  // the next exercise, or the series recorded
  private def next(index: Int): Unit =
    if (index + 1 < exercises.now().size) {
      step.set(Step.Asking(index + 1))
    } else {
      step.set(Step.Loading)
      finished.writer.onNext(ExercisesDone(point, outcomes.now()))
    }

  private def sentence(
      parts: List[SentencePart],
      blanked: Boolean
  ): HtmlElement = p(
    cls := "word-sentence",
    lang := "ja",
    parts.map(part =>
      span(
        cls("is-asked") := part.marked && !blanked,
        Option.when(part.spaceBefore)(" "),
        if (part.marked && blanked) span("＿＿") else PartText(part).node
      )
    )
  )

  private def asked(index: Int, exercise: LessonExercise): HtmlElement =
    exercise.kind match {
      case ExerciseKind.Particle | ExerciseKind.Form =>
        div(
          p(
            cls := "placement-prompt",
            if (exercise.kind == ExerciseKind.Form)
              Tr("Which form?", "Quelle forme ?")
            else Tr("Which particle?", "Quelle particule ?")
          ),
          sentence(exercise.parts, blanked = true),
          p(cls := "example-french", exercise.french),
          div(
            cls := "exercise-choices",
            exercise.choices.map(choice =>
              button(
                lang := "ja",
                choice,
                onClick --> (_ => submit(index, choice))
              )
            )
          )
        )
      case ExerciseKind.Meaning =>
        val field = input(
          cls := "meaning-input",
          placeholder := Tr("its meaning, in French", "son sens, en français"),
          autoComplete := "off",
          onMountFocus
        )
        div(
          p(
            cls := "placement-prompt",
            Tr("What does it mean?", "Que veut dire :")
          ),
          sentence(exercise.parts, blanked = false),
          form(
            onSubmit.preventDefault --> (_ => submit(index, field.ref.value)),
            field,
            button(typ := "submit", cls := "primary", Tr("Answer", "Valider"))
          ),
          button(
            Tr("I don't know", "Je ne sais pas"),
            onClick --> (_ => submit(index, ""))
          )
        )
      case ExerciseKind.KanjiWriting =>
        div(
          p(
            cls := "placement-prompt",
            Tr("A kanji to write, in:", "Un kanji à écrire, dans :")
          ),
          sentence(exercise.parts, blanked = false),
          p(cls := "example-french", exercise.french),
          exercise.kanji.map(kanji =>
            KanjiExerciseView(
              bus,
              kanji,
              () => {
                kept(ExerciseKind.KanjiWriting, right = true)
                next(index)
              }
            ).node
          )
        )
      case ExerciseKind.Translation =>
        val field = KanaInput()
        val stylusField = StylusField(bus, field, stylus)
        val inKatakana = exercise.wordsInKatakana
        div(
          p(
            cls := "placement-prompt",
            Tr("Say it in Japanese, in kana:", "Dis-le en japonais, en kana :")
          ),
          div(cls := "series-meaning", exercise.french),
          p(
            cls := "expected-script",
            Tr("Written in ", "Écrit en "),
            span(cls := "script-name", "hiragana"),
            Option.when(inKatakana > 0)(
              List[Modifier[HtmlElement]](
                if (inKatakana == 1)
                  Tr(", with one word in ", ", avec un mot en ")
                else
                  Tr(
                    s", with $inKatakana words in ",
                    s", avec $inKatakana mots en "
                  ),
                span(cls := "script-name", "katakana"),
                Tr(" (switch: あ / ア)", " (bouton あ / ア)")
              )
            )
          ),
          form(
            onSubmit.preventDefault --> { _ =>
              stylusField.keep(field.value)
              submit(index, field.value)
            },
            field.node.amend(cls := "sentence-input"),
            button(typ := "submit", cls := "primary", Tr("Answer", "Valider"))
          ),
          stylusField.node,
          button(
            Tr("I don't know", "Je ne sais pas"),
            onClick --> (_ => submit(index, ""))
          )
        )
    }

  // the tutor away: the learner compares their meaning with the one expected
  private def selfGraded(
      index: Int,
      exercise: LessonExercise,
      answer: String
  ): HtmlElement = div(
    p(
      Tr("You wrote: ", "Tu as écrit : "),
      span(cls := "typed-answer", answer)
    ),
    p(Tr("It means: ", "Elle veut dire : "), exercise.french),
    p(
      cls := "in-english",
      Tr(
        "The tutor is away: say whether your meaning is the same.",
        "Le tuteur est absent : dis si ton sens est le même."
      )
    ),
    div(
      cls := "placement-actions",
      button(
        cls := "primary",
        Tr("Same meaning", "Même sens"),
        onClick --> { _ =>
          kept(exercise.kind, right = true)
          next(index)
        }
      ),
      button(
        Tr("Not quite", "Pas tout à fait"),
        onClick --> { _ =>
          kept(exercise.kind, right = false)
          next(index)
        }
      )
    )
  )

  private def judged(
      index: Int,
      exercise: LessonExercise,
      answer: String,
      verdict: ExerciseVerdict
  ): HtmlElement = if (verdict.toSelfGrade) {
    selfGraded(index, exercise, answer)
  } else
    div(
      p(
        cls := (if (verdict.right) "last-answer is-right"
                else "last-answer is-failed"),
        if (verdict.right) Tr("Right!", "Juste !")
        else Tr("Not quite.", "Pas tout à fait."),
        Option.when(verdict.byTutor)(
          span(
            cls := "in-english",
            Tr(" (the tutor's judgement)", " (selon le tuteur)")
          )
        )
      ),
      Option.when(
        answer.nonEmpty && (exercise.kind == ExerciseKind.Translation ||
          exercise.kind == ExerciseKind.Meaning)
      )(
        p(
          Tr("You wrote: ", "Tu as écrit : "),
          span(
            Option.when(exercise.kind == ExerciseKind.Translation)(
              lang := "ja"
            ),
            cls := "typed-answer",
            answer
          )
        )
      ),
      verdict.correction.map(corrected =>
        div(
          p(
            cls := "in-english",
            Tr("Your sentence, corrected:", "Ta phrase corrigée :")
          ),
          sentence(corrected, blanked = false)
        )
      ),
      p(cls := "in-english", Tr("The sentence:", "La phrase :")),
      sentence(exercise.parts, blanked = false),
      p(cls := "example-french", exercise.french),
      verdict.explanation
        .map(explanation => p(cls := "tutor-explanation", explanation)),
      button(
        cls := "primary",
        onMountFocus,
        Tr("Continue", "Continuer"),
        onClick --> (_ => next(index))
      )
    )

  private def shown(now: Step, all: List[LessonExercise]): HtmlElement =
    now match {
      case Step.Idle =>
        button(
          cls := "primary",
          Tr("Practise", "S'exercer"),
          onClick --> (_ => loads.writer.onNext(()))
        )
      case Step.Loading =>
        p(
          cls := "in-english",
          Tr(
            "The tutor prepares the exercises…",
            "Le tuteur prépare les exercices…"
          )
        )
      case Step.Asking(index) =>
        div(progress(index, all.size), asked(index, all(index)))
      case Step.Judging(index) =>
        div(
          progress(index, all.size),
          p(cls := "in-english", Tr("Checking…", "Correction…"))
        )
      case Step.Judged(index, answer, verdict) =>
        div(
          progress(index, all.size),
          judged(index, all(index), answer, verdict)
        )
      case Step.Done(rightOnes, total) =>
        div(
          p(
            Tr(
              s"$rightOnes right out of $total.",
              s"$rightOnes justes sur $total."
            )
          ),
          button(
            Tr("Practise again", "Recommencer"),
            onClick --> (_ => loads.writer.onNext(()))
          ),
          onDone.map(goOn =>
            button(
              cls := "primary",
              Tr("Go on", "Continuer la séance"),
              onClick --> (_ => goOn())
            )
          )
        )
      case Step.Failed(reason) =>
        div(
          p(cls := "error", reason),
          button(
            Tr("Try again", "Réessayer"),
            onClick --> (_ => loads.writer.onNext(()))
          )
        )
    }

  private def progress(index: Int, total: Int) =
    p(
      cls := "in-english",
      Tr(
        s"Exercise ${index + 1} of $total",
        s"Exercice ${index + 1} sur $total"
      )
    )

  override lazy val node: HtmlElement = div(
    cls := "lesson-exercises",
    h2(Tr("Exercises", "Exercices")),
    loads.events --> (_ => step.set(Step.Loading)),
    loads.events.flatMapSwitch(_ =>
      bus.request(AppCommand.LoadExercises(point)) {
        case AppEvent.ExercisesLoaded(`point`, loaded) => loaded
      }
    ) --> {
      case Right(loaded) =>
        exercises.set(loaded)
        outcomes.set(Nil)
        step.set(if (loaded.isEmpty) Step.Done(0, 0) else Step.Asking(0))
      case Left(reason) => step.set(Step.Failed(reason))
    },
    answers.events.flatMapSwitch(answer =>
      bus
        .request(AppCommand.AnswerExercise(answer)) {
          case AppEvent.ExerciseJudged(`answer`, verdict) => verdict
        }
        .map(answer -> _)
    ) --> {
      case (answer, Right(verdict)) =>
        // left to the learner, it is kept once they say
        if (!verdict.toSelfGrade) kept(answer.exercise.kind, verdict.right)
        val index = exercises.now().indexOf(answer.exercise)
        step.set(Step.Judged(index, answer.answer, verdict))
      case (_, Left(reason)) => step.set(Step.Failed(reason))
    },
    finished.events.flatMapSwitch(done =>
      bus.request(AppCommand.FinishExercises(done)) {
        case AppEvent.ExercisesFinished(`done`, tally) => tally
      }
    ) --> {
      case Right(tally) => step.set(Step.Done(tally.right, tally.total))
      case Left(reason) => step.set(Step.Failed(reason))
    },
    child <-- step.signal.combineWith(exercises.signal).map(shown)
  )
}
