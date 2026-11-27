package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.questions.*

import com.raquo.laminar.api.L.*

/** Questions to the tutor about what the learner has before them now
  * (`context`, said above the questions), as a conversation: each kept by the
  * server, answered or not (the tutor away, it is kept all the same, to be
  * found on the questions page). The conversation starts again when the learner
  * moves on to something else (another sentence, word or lesson).
  */
class AskTutor(bus: AppBus, context: StrictSignal[QuestionContext])
    extends ReactiveElement {

  // the conversation, the first first
  private val thread = Var(List.empty[AskedQuestion])
  private val asking = EventBus[TutorQuestion]()
  private val waiting = Var(false)
  private val failed = Var(Option.empty[String])

  private def exchange(asked: AskedQuestion): HtmlElement = div(
    cls := "tutor-exchange",
    p(cls := "tutor-question", asked.question),
    asked.answer.fold(
      p(
        cls := "in-english",
        Tr(
          "The tutor could not answer: the question is kept, on the Questions page.",
          "Le tuteur n'a pas pu répondre : la question est gardée, dans la page Questions."
        )
      )
    )(answer => p(cls := "tutor-answer", answer))
  )

  private val field = textArea(
    cls := "tutor-field",
    rows := 2,
    placeholder := Tr("Your question", "Ta question")
  )

  /** The question field given the focus. */
  def focus(): Unit = field.ref.focus()

  override lazy val node: HtmlElement = div(
    cls := "ask-tutor-panel",
    p(
      cls := "tutor-about",
      child.text <-- context.map(AskTutor.about)
    ),
    context.changes
      .map(each => (each.point, each.sentence, each.word))
      .distinct --> (_ => thread.set(Nil)),
    div(
      cls := "tutor-thread",
      children <-- thread.signal.map(_.map(exchange)),
      child.maybe <-- waiting.signal.map(
        Option.when(_)(
          p(
            cls := "in-english",
            Tr("The tutor thinks…", "Le tuteur réfléchit…")
          )
        )
      ),
      child.maybe <-- failed.signal.map(
        _.map(reason => p(cls := "error", reason))
      )
    ),
    form(
      onSubmit.preventDefault --> { _ =>
        val question = field.ref.value.trim
        if (question.nonEmpty && !waiting.now()) {
          field.ref.value = ""
          failed.set(None)
          waiting.set(true)
          asking.writer.onNext(
            TutorQuestion(
              context.now(),
              question,
              thread.now().lastOption.map(_.id)
            )
          )
        }
      },
      field,
      div(
        cls := "answer-form",
        button(
          typ := "submit",
          cls := "primary",
          disabled <-- waiting.signal,
          child.text <-- thread.signal.map(asked =>
            if (asked.isEmpty) Tr("Ask", "Demander")
            else Tr("Ask on", "Continuer")
          )
        ),
        child.maybe <-- thread.signal.map(asked =>
          Option.when(asked.nonEmpty)(
            button(
              typ := "button",
              Tr("A new question", "Nouvelle question"),
              onClick --> (_ => thread.set(Nil))
            )
          )
        )
      )
    ),
    asking.events.flatMapSwitch(question =>
      bus.request(AppCommand.AskTutor(question)) {
        case AppEvent.TutorAnswered(`question`, asked) => asked
      }
    ) --> {
      case Right(asked) =>
        waiting.set(false)
        thread.update(_ :+ asked)
      case Left(reason) =>
        waiting.set(false)
        failed.set(Some(reason))
    }
  )
}

object AskTutor {

  /** What the tutor is told the learner has before them, in a few words. */
  def about(context: QuestionContext): String = {
    val where = context.from match {
      case AskedFrom.Questions => Tr("A question of your own", "Question libre")
      case AskedFrom.Lesson    => Tr("On the lesson", "Sur la leçon")
      case AskedFrom.Exercise  => Tr("On the exercise", "Sur l'exercice")
      case AskedFrom.Correction =>
        Tr(
          "On your answer and its correction",
          "Sur ta réponse et sa correction"
        )
    }
    (where :: context.sentence
      .orElse(context.word)
      .orElse(context.point)
      .toList).mkString(" · ")
  }
}
