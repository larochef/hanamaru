package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.questions.*

import scala.scalajs.js

import com.raquo.laminar.api.L.*

/** The questions asked to the tutor, the latest first, each with what the
  * learner had before them and the tutor's answer, if it gave one; and a
  * question of one's own. Asked again each time the page is shown, and after
  * each question.
  */
class QuestionsPage(bus: AppBus) extends ReactiveElement {

  private val questions: Signal[List[AskedQuestion]] = bus.events.events
    .collect {
      case AppEvent.PageShown(Page.Questions) => ()
      case AppEvent.TutorAnswered(_, _)       => ()
    }
    .flatMapSwitch(_ =>
      bus.request(AppCommand.LoadQuestions) {
        case AppEvent.QuestionsLoaded(questions) => questions
      }
    )
    .collect { case Right(questions) => questions }
    .startWith(Nil)

  private def asked(question: AskedQuestion): HtmlElement = div(
    cls := "asked-question",
    p(
      cls := "in-english",
      new js.Date(question.askedAt.toDouble).toLocaleString(),
      " · ",
      AskTutor.about(question.context),
      Option.when(question.follows.nonEmpty)(
        Tr(" · following the one before", " · suite de la précédente")
      )
    ),
    question.context.sentence.map(sentence =>
      p(cls := "asked-sentence", lang := "ja", sentence)
    ),
    p(cls := "tutor-question", question.question),
    question.answer.fold(
      p(cls := "in-english", Tr("No answer.", "Pas de réponse."))
    )(answer => p(cls := "tutor-answer", answer))
  )

  override lazy val node: HtmlElement = div(
    cls := "questions-page",
    h1(Tr("Questions", "Questions")),
    p(
      Tr(
        "Ask the tutor anything about Japanese. Every question is kept here, with what you had before you, answered or not.",
        "Pose au tuteur toute question sur le japonais. Chaque question est gardée ici, avec ce que tu avais sous les yeux, qu'il ait répondu ou non."
      )
    ),
    div(
      cls := "ask-tutor",
      AskTutor(bus, Var(QuestionContext(AskedFrom.Questions)).signal).node
    ),
    children <-- questions.map(_.map(asked))
  )
}
