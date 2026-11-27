package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.lessons.*
import hanamaru.shared.questions.{AskedFrom, QuestionContext}
import hanamaru.shared.text.SentencePart

import com.raquo.laminar.api.L.*

/** A lesson's exercises, one after the other: a sentence's particles typed, a
  * form chosen, a sentence translated from French, one's meaning given in
  * French, a kanji written; each judged by the code, else corrected by the
  * tutor (a meaning, the tutor away, by the learner), then the series recorded
  * as one review of the point (the kanji written are the kanji's reviews);
  * `onDone` offers to go on, in a session. Free ([[ExercisesOf.Free]]), they
  * are recorded as nothing, only counted, and come one by one, each written by
  * the tutor while the learner answers those before.
  */
class LessonExercises(
    bus: AppBus,
    of: ExercisesOf,
    onDone: Option[() => Unit] = None
) extends ReactiveElement {

  private enum Step {
    case Idle
    case Loading
    case Writing(index: Int)
    case Asking(index: Int)
    case Judging(index: Int)
    case Judged(index: Int, answer: String, verdict: ExerciseVerdict)
    case Done(right: Int, total: Int)
    case Failed(reason: String)
  }

  // exercises as they come, how many there are, and the free practice
  final private case class Loaded(
      exercises: List[LessonExercise],
      total: Int,
      practice: Option[Long]
  )

  private val step = Var[Step](Step.Idle)
  private val exercises = Var(List.empty[LessonExercise])
  private val total = Var(0)
  private val practice = Var(Option.empty[Long])
  private val toWrite = EventBus[(Long, Int)]()
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

  // the next exercise (free, once written), or the series recorded (free,
  // only counted)
  private def next(index: Int): Unit =
    if (index + 1 < exercises.now().size) {
      step.set(Step.Asking(index + 1))
    } else if (index + 1 < total.now()) {
      step.set(Step.Writing(index + 1))
      practice.now().foreach(id => toWrite.writer.onNext(id -> (index + 1)))
    } else
      of match {
        case ExercisesOf.Lesson(point) =>
          step.set(Step.Loading)
          finished.writer.onNext(ExercisesDone(point, outcomes.now()))
        case ExercisesOf.Free =>
          val done = outcomes.now()
          step.set(Step.Done(done.count(_.right), done.size))
      }

  // the exercise under way, as the tutor is told it
  private def asking(exercise: LessonExercise): QuestionContext =
    QuestionContext(
      AskedFrom.Exercise,
      Some(exercise.point),
      Some(exercise.japanese),
      Some(exercise.french),
      task = Some(LessonExercises.taskSaid(exercise))
    )

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

  // the sentence, each blank a kana field for its particle, typed or written
  // with the stylus in the blank chosen; sent one per blank, spaced
  private def particlesAsked(
      index: Int,
      exercise: LessonExercise
  ): HtmlElement = {
    val fields = exercise.parts
      .filter(_.marked)
      .map(_ => KanaInput(place = KanaInput.Place.InSentence))
    val current = Var(0)
    val stylusField = new StylusField(bus, () => fields(current.now()), stylus)
    def typed = fields.map(_.value.filterNot(_.isWhitespace)).mkString(" ")
    val (shown, _) = exercise.parts.foldLeft(
      (List.empty[HtmlElement], fields.zipWithIndex)
    ) {
      case ((done, (field, blank) :: rest), part) if part.marked =>
        (
          done :+ span(
            Option.when(part.spaceBefore)(" "),
            field.node.amend(
              cls("is-current") <-- current.signal.map(_ == blank),
              field.focused.mapTo(blank) --> current.writer
            )
          ),
          rest
        )
      case ((done, left), part) =>
        (
          done :+ span(
            Option.when(part.spaceBefore)(" "),
            PartText(part).node
          ),
          left
        )
    }
    div(
      p(
        cls := "placement-prompt",
        if (fields.size == 1)
          Tr("Write the particle:", "Écris la particule :")
        else Tr("Write the particles:", "Écris les particules :")
      ),
      form(
        onMountCallback(_ => fields.headOption.foreach(_.focus())),
        onSubmit.preventDefault --> { _ =>
          stylusField.keep(typed.filterNot(_.isWhitespace))
          submit(index, typed)
        },
        p(cls := "word-sentence", lang := "ja", shown),
        p(cls := "example-french", exercise.french),
        div(
          cls := "answer-form",
          button(typ := "submit", cls := "primary", Tr("Answer", "Valider")),
          button(
            typ := "button",
            Tr("I don't know", "Je ne sais pas"),
            onClick --> (_ => submit(index, fields.map(_ => "").mkString(" ")))
          )
        )
      ),
      stylusField.node
    )
  }

  private def asked(index: Int, exercise: LessonExercise): HtmlElement =
    exercise.kind match {
      case ExerciseKind.Particle => particlesAsked(index, exercise)
      case ExerciseKind.Form     =>
        div(
          p(cls := "placement-prompt", Tr("Which form?", "Quelle forme ?")),
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
        val field = KanaInput(kanji = exercise.kanjiWritten)
        val stylusField = StylusField(bus, field, stylus)
        val inKatakana = exercise.wordsInKatakana
        div(
          p(
            cls := "placement-prompt",
            if (exercise.kanjiWritten.isEmpty)
              Tr(
                "Say it in Japanese, in kana:",
                "Dis-le en japonais, en kana :"
              )
            else
              Tr(
                "Say it in Japanese, with the kanji you write:",
                "Dis-le en japonais, avec les kanji que tu sais écrire :"
              )
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
            cls := "answer-form",
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
        exercise.kind == ExerciseKind.Particle && !verdict.right &&
          answer.exists(!_.isWhitespace)
      )(
        p(
          Tr("You typed: ", "Tu as tapé : "),
          span(
            lang := "ja",
            cls := "typed-answer",
            answer
              .split(" ", -1)
              .map(each => if (each.isEmpty) "…" else each)
              .mkString("、")
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
      case Step.Writing(index) =>
        div(
          progress(index),
          p(
            cls := "in-english",
            Tr(
              "The tutor writes the next sentence…",
              "Le tuteur écrit la phrase suivante…"
            )
          )
        )
      case Step.Asking(index) =>
        div(progress(index), asked(index, all(index)))
      case Step.Judging(index) =>
        div(
          progress(index),
          p(cls := "in-english", Tr("Checking…", "Correction…"))
        )
      case Step.Judged(index, answer, verdict) =>
        div(
          progress(index),
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

  private def progress(index: Int) =
    p(
      cls := "in-english",
      child.text <-- total.signal.map(count =>
        Tr(
          s"Exercise ${index + 1} of $count",
          s"Exercice ${index + 1} sur $count"
        )
      )
    )

  override lazy val node: HtmlElement = div(
    cls := "lesson-exercises",
    h2(Tr("Exercises", "Exercices")),
    loads.events --> (_ => step.set(Step.Loading)),
    loads.events.flatMapSwitch(_ =>
      of match {
        case ExercisesOf.Lesson(point) =>
          bus.request(AppCommand.LoadExercises(point)) {
            case AppEvent.ExercisesLoaded(`point`, loaded) =>
              Loaded(loaded, loaded.size, None)
          }
        case ExercisesOf.Free =>
          bus.request(AppCommand.StartFreePractice) {
            case AppEvent.FreePracticeStarted(first) =>
              Loaded(List(first.exercise), first.total, Some(first.practice))
          }
      }
    ) --> {
      case Right(loaded) =>
        exercises.set(loaded.exercises)
        total.set(loaded.total)
        practice.set(loaded.practice)
        outcomes.set(Nil)
        step.set(
          if (loaded.exercises.isEmpty) Step.Done(0, 0) else Step.Asking(0)
        )
      case Left(reason) => step.set(Step.Failed(reason))
    },
    toWrite.events.flatMapSwitch((id, index) =>
      bus.request(AppCommand.LoadFreeExercise(id, index)) {
        case AppEvent.FreeExerciseLoaded(written)
            if written.practice == id && written.index == index =>
          written.exercise
      }
    ) --> {
      case Right(written) =>
        exercises.update(_ :+ written)
        step.set(Step.Asking(exercises.now().size - 1))
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
    child <-- step.signal.combineWith(exercises.signal).map(shown),
    // what the tutor is told: the exercise under way, then the answer and
    // its verdict
    TutorContext(
      bus,
      step.signal.combineWith(exercises.signal).map {
        case (Step.Asking(index), all)  => all.lift(index).map(asking)
        case (Step.Judging(index), all) => all.lift(index).map(asking)
        case (Step.Judged(index, answer, verdict), all) =>
          all
            .lift(index)
            .map(exercise =>
              asking(exercise).copy(
                from = AskedFrom.Correction,
                answer = Some(answer).filter(_.exists(!_.isWhitespace)),
                verdict = Some(LessonExercises.verdictSaid(exercise, verdict))
              )
            )
        case _ => None
      }
    ).node
  )
}

object LessonExercises {

  /** What an exercise asks, in words, for the tutor. */
  def taskSaid(exercise: LessonExercise): String = {
    val blanked = exercise.parts
      .map(part => if (part.marked) "＿" else part.text)
      .mkString
    exercise.kind match {
      case ExerciseKind.Particle =>
        s"Write the missing particles, each blank one: $blanked (expected: ${exercise.expected})"
      case ExerciseKind.Form =>
        s"Choose the missing form among ${exercise.choices.mkString(", ")}: $blanked (expected: ${exercise.expected})"
      case ExerciseKind.Translation =>
        s"Translate into Japanese, in kana: ${exercise.french} (expected: ${exercise.expected})"
      case ExerciseKind.Meaning =>
        s"Give the French meaning of: ${exercise.japanese}"
      case ExerciseKind.KanjiWriting =>
        s"Write the kanji ${exercise.expected} of the marked word, in: ${exercise.japanese}"
    }
  }

  /** A verdict as the learner was shown it, in words, for the tutor. */
  def verdictSaid(exercise: LessonExercise, verdict: ExerciseVerdict): String =
    List(
      Some(
        if (verdict.toSelfGrade) "not judged (the tutor was away)"
        else if (verdict.right) "right"
        else "wrong"
      ),
      verdict.correction.map(parts =>
        s"the answer corrected: ${parts.map(_.text).mkString}"
      ),
      verdict.explanation
    ).flatten.mkString("; ")
}
