package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.characters.*
import hanamaru.shared.kana.Kana

import com.raquo.laminar.api.L.*

/** A focus on a new character, kana or kanji ([[FocusState]]): its cards one
  * after the other, each as the server plans it from how the cards so far went,
  * answered without reviews; once over, the focus is kept as the character's
  * first review, and `done` leads on.
  */
class FocusView(bus: AppBus, character: String, done: () => Unit)
    extends ReactiveElement {

  private val answers = Var(List.empty[FocusAnswer])

  // how the card under way went, once answered
  private val result = Var(Option.empty[CardResult])

  private def focus = FocusState(character, answers.now())

  // the card under way, with how many came before it: a card missed may come
  // back right after, the same card again
  private val card
      : Signal[Option[(Int, AppResult[Option[CharacterExercise]])]] =
    answers.signal
      .flatMapSwitch(answered =>
        bus
          .request(AppCommand.LoadFocusCard(FocusState(character, answered))) {
            case AppEvent.FocusCardLoaded(
                  FocusState(`character`, `answered`),
                  next
                ) =>
              next
          }
          .map(next => Some(answered.size -> next))
      )
      .startWith(None)

  private def answeredWith(exercise: CharacterExercise): Unit = {
    val went = result.now().getOrElse(CardResult.Missed)
    result.set(None)
    answers.update(_ :+ FocusAnswer(exercise, went))
  }

  private def cardView(exercise: CharacterExercise): HtmlElement = {
    val next = () => answeredWith(exercise)
    val answered = (went: CardResult) => result.set(Some(went))
    exercise match {
      case CharacterExercise.Kana(literal, asked, isNew, inWord) =>
        Kana
          .find(literal)
          .fold(p(cls := "is-failed", literal))(kana =>
            KanaExerciseView(
              bus,
              kana,
              asked,
              isNew,
              inWord,
              next,
              AnswerKept.InFocus,
              answered
            ).node
          )
      case CharacterExercise.Kanji(kanji) =>
        KanjiExerciseView(bus, kanji, next, AnswerKept.InFocus, answered).node
      // a focus's cards are kana or kanji exercises
      case CharacterExercise.Focus(other) => p(cls := "is-failed", other)
    }
  }

  private def finished: HtmlElement = div(
    cls := "series-task",
    child <-- bus
      .request(AppCommand.FinishFocus(focus)) {
        case AppEvent.FocusFinished(FocusState(`character`, _)) => ()
      }
      .map {
        case Right(_) =>
          div(
            h2(
              Tr(s"Focus on $character done", s"Focus sur $character terminé")
            ),
            p(
              Tr(
                "Its reading and writing are reviewed from tomorrow on.",
                "Sa lecture et son écriture seront révisées à partir de demain."
              )
            ),
            button(
              cls := "primary",
              Tr("Continue", "Continuer"),
              onMountFocus,
              onClick --> (_ => done())
            )
          )
        case Left(reason) => p(cls := "error", reason)
      }
  )

  override lazy val node: HtmlElement = div(
    cls := "character-focus",
    p(
      cls := "in-english",
      child.text <-- answers.signal.map(answered =>
        Tr(
          s"Focus on $character · card ${answered.size + 1}",
          s"Focus sur $character · carte ${answered.size + 1}"
        )
      )
    ),
    child <-- card.map {
      case None                             => p(cls := "in-english", "…")
      case Some((_, Left(reason)))          => p(cls := "error", reason)
      case Some((_, Right(None)))           => finished
      case Some((_, Right(Some(exercise)))) => cardView(exercise)
    }
  )
}
