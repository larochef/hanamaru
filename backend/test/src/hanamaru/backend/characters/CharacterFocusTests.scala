package hanamaru.backend.characters

import hanamaru.shared.characters.*

import utest.*

object CharacterFocusTests extends TestSuite {

  private def kana(asked: CharacterAsked, isNew: Boolean = false) =
    CharacterExercise.Kana("ぬ", asked, isNew)

  private val cards = List(
    kana(CharacterAsked.Writing, isNew = true),
    kana(CharacterAsked.Reading),
    kana(CharacterAsked.Writing),
    kana(CharacterAsked.Reading)
  )

  // the focus answered to its end, each card by `result`, from the card and
  // how many came before it
  private def played(result: (Int, CharacterExercise) => CardResult) =
    LazyList
      .iterate(Option(List.empty[FocusAnswer]))(
        _.flatMap(answers =>
          CharacterFocus
            .next(cards, answers)
            .map(card =>
              answers :+ FocusAnswer(card, result(answers.size, card))
            )
        )
      )
      .takeWhile(_.nonEmpty)
      .flatten
      .last

  val tests: Tests = Tests {
    test("its cards in order, then over when all went right") {
      val answers = played((_, _) => CardResult.Right)
      assert(answers.map(_.exercise) == cards)
    }
    test("a card missed comes back, no longer new, until right") {
      val answers = played((before, _) =>
        if (before == 2) CardResult.Missed else CardResult.Right
      )
      assert(answers.map(_.exercise) == cards :+ cards(2))
    }
    test("a card missed, then right later in the focus, does not come back") {
      // its reading missed first, then right: the same card, asked twice
      val answers = played((before, _) =>
        if (before == 1) CardResult.Missed else CardResult.Right
      )
      assert(answers.map(_.exercise) == cards)
    }
    test("a card always missed comes back a few times, and the focus ends") {
      val answers = played {
        case (_, CharacterExercise.Kana(_, CharacterAsked.Writing, _, _)) =>
          CardResult.Missed
        case _ => CardResult.Right
      }
      assert(answers.size == cards.size + CharacterFocus.comeBacksAtMost)
    }
    test(
      "the cards missed come back in turn, the one asked longest ago first"
    ) {
      val answers = played((before, _) =>
        if (before < cards.size) CardResult.Missed else CardResult.Right
      )
      // all missed: the writing, asked longer ago, then the reading; each
      // right the second time
      assert(
        answers.drop(cards.size).map(_.exercise) ==
          List(kana(CharacterAsked.Writing), kana(CharacterAsked.Reading))
      )
    }
  }
}
