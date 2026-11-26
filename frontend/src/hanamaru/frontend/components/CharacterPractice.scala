package hanamaru.frontend.components

import hanamaru.frontend.i18n.Tr
import hanamaru.frontend.model.*
import hanamaru.shared.characters.{CharacterExercise, CharactersOf}
import hanamaru.shared.kana.Kana

import com.raquo.laminar.api.L.*

/** A lesson session's kana and kanji, as the server planned them, one exercise
  * after the other ([[KanaExerciseView]], [[KanjiExerciseView]]). Once all are
  * done, `goOn` leads on.
  */
class CharacterPractice(
    bus: AppBus,
    goOn: () => Unit,
    of: CharactersOf = CharactersOf.MyLesson
) extends ReactiveElement {

  private val at = Var(0)

  private val exercises: EventStream[AppResult[List[CharacterExercise]]] =
    bus.request(AppCommand.LoadCharacterPractice(of)) {
      case AppEvent.CharacterPracticeLoaded(`of`, exercises) => exercises
    }

  private def exerciseView(
      exercise: CharacterExercise,
      index: Int,
      total: Int
  ): HtmlElement = {
    val next = () => at.update(_ + 1)
    div(
      p(
        cls := "in-english",
        Tr(s"${index + 1} of $total", s"${index + 1} sur $total")
      ),
      exercise match {
        case CharacterExercise.Kana(character, asked, isNew, inWord) =>
          Kana
            .find(character)
            .fold(p(cls := "is-failed", character))(kana =>
              KanaExerciseView(bus, kana, asked, isNew, inWord, next).node
            )
        case CharacterExercise.Kanji(kanji) =>
          KanjiExerciseView(bus, kanji, next).node
        case CharacterExercise.Focus(character) =>
          FocusView(bus, character, next).node
      }
    )
  }

  private def finished: HtmlElement = div(
    cls := "series-task",
    h2(Tr("Kana and kanji done", "Kana et kanji terminés")),
    button(
      cls := "primary",
      Tr("Continue", "Continuer"),
      onMountFocus,
      onClick --> (_ => goOn())
    )
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
