package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.lessons.*
import hanamaru.shared.text.SentencePart

import com.raquo.laminar.api.L.*

/** A lesson's exercises, one after the other: a particle chosen, a sentence
  * translated from French; each judged by the code, else corrected by the
  * tutor, then the series recorded as one review of the point; `onDone` offers
  * to go on, in a session.
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
  private val right = Var(0)
  private val loads = EventBus[Unit]()
  private val answers = EventBus[ExerciseAnswer]()

  private def submit(index: Int, answer: String): Unit = {
    step.set(Step.Judging(index))
    answers.writer.onNext(ExerciseAnswer(exercises.now()(index), answer))
  }

  // the next exercise, or the series recorded
  private def next(index: Int): Unit = {
    val total = exercises.now().size
    if (index + 1 < total) {
      step.set(Step.Asking(index + 1))
    } else {
      val done = ExercisesDone(point, right.now(), total)
      bus.send(AppCommand.FinishExercises(done))
      step.set(Step.Done(done.right, total))
    }
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
        (if (part.spaceBefore) " " else "") +
          (if (part.marked && blanked) "＿＿" else part.text)
      )
    )
  )

  private def asked(index: Int, exercise: LessonExercise): HtmlElement =
    exercise.kind match {
      case ExerciseKind.Particle =>
        div(
          p(
            cls := "placement-prompt",
            Tr("Which particle?", "Quelle particule ?")
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
      case ExerciseKind.Translation =>
        val field = KanaInput()
        div(
          p(
            cls := "placement-prompt",
            Tr("Say it in Japanese, in kana:", "Dis-le en japonais, en kana :")
          ),
          div(cls := "series-meaning", exercise.french),
          form(
            onSubmit.preventDefault --> (_ => submit(index, field.value)),
            field.node.amend(cls := "sentence-input"),
            button(typ := "submit", cls := "primary", Tr("Answer", "Valider"))
          ),
          button(
            Tr("I don't know", "Je ne sais pas"),
            onClick --> (_ => submit(index, ""))
          )
        )
    }

  private def judged(
      index: Int,
      exercise: LessonExercise,
      answer: String,
      verdict: ExerciseVerdict
  ): HtmlElement = div(
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
    Option.when(answer.nonEmpty && exercise.kind == ExerciseKind.Translation)(
      p(
        Tr("You wrote: ", "Tu as écrit : "),
        span(lang := "ja", cls := "typed-answer", answer)
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
    verdict.explanation.map(explanation =>
      p(cls := "tutor-explanation", explanation)
    ),
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
        right.set(0)
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
        if (verdict.right) right.update(_ + 1)
        val index = exercises.now().indexOf(answer.exercise)
        step.set(Step.Judged(index, answer.answer, verdict))
      case (_, Left(reason)) => step.set(Step.Failed(reason))
    },
    child <-- step.signal.combineWith(exercises.signal).map(shown)
  )
}
