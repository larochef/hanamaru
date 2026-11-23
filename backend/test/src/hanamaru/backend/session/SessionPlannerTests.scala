package hanamaru.backend.session

import hanamaru.backend.learner.{CardKind, KanaCard, Phase}
import hanamaru.shared.session.SessionExercise

import utest.*

object SessionPlannerTests extends TestSuite {
  private val now = 10_000_000L
  private val endsAt = now + 10 * 60 * 1000

  private def card(character: String, kind: CardKind, phase: Phase, due: Long) =
    KanaCard(character, kind, phase, due)

  private def next(
      cards: List[KanaCard],
      stylus: Boolean = true,
      introduced: Int = 0,
      last: Option[SessionExercise] = None,
      at: Long = now
  ) = SessionPlanner.next(cards, at, endsAt, stylus, introduced, 3, last)

  val tests: Tests = Tests {
    test("nothing met: the first kana of the chart, new") {
      assert(next(Nil).contains(SessionExercise.NewKana("あ", true)))
      assert(
        next(Nil, stylus = false).contains(SessionExercise.NewKana("あ", false))
      )
    }
    test("cards due first, those being learned before the others") {
      val cards = List(
        card("あ", CardKind.Writing, Phase.Review, now - 5000),
        card("い", CardKind.Reading, Phase.Learning, now - 10),
        card("う", CardKind.Reading, Phase.Review, now + 5000)
      )
      assert(next(cards).contains(SessionExercise.Reading("い")))
      assert(
        next(cards.drop(1).take(0) ++ cards.take(1))
          .contains(SessionExercise.Writing("あ"))
      )
    }
    test("without the stylus, no writing") {
      val cards = List(
        card("あ", CardKind.Writing, Phase.Review, now - 5000),
        card("あ", CardKind.Reading, Phase.Review, now + 5000)
      )
      assert(
        next(cards, stylus = false).contains(
          SessionExercise.NewKana("い", false)
        )
      )
    }
    test("a kana met but never read is read before any new kana") {
      val cards = List(card("か", CardKind.Writing, Phase.Review, now + 5000))
      assert(next(cards).contains(SessionExercise.Reading("か")))
    }
    test("the same card never twice in a row when there is another") {
      val cards = List(
        card("あ", CardKind.Reading, Phase.Learning, now - 10),
        card("い", CardKind.Reading, Phase.Learning, now - 5)
      )
      assert(
        next(cards, last = Some(SessionExercise.Reading("あ")))
          .contains(SessionExercise.Reading("い"))
      )
      assert(
        next(cards.take(1), last = Some(SessionExercise.Reading("あ")))
          .contains(SessionExercise.Reading("あ"))
      )
    }
    test("new kana: a few per session, and none too close to the end") {
      val met = List(
        card("あ", CardKind.Writing, Phase.Review, endsAt + 3600000),
        card("あ", CardKind.Reading, Phase.Review, endsAt + 3600000)
      )
      assert(next(met).contains(SessionExercise.NewKana("い", true)))
      assert(next(met, introduced = 3).isEmpty)
      assert(next(met, at = endsAt - 60 * 1000).isEmpty)
    }
    test("a card being learned due in a moment rather than ending early") {
      val cards = List(
        card("あ", CardKind.Writing, Phase.Learning, now + 30 * 1000),
        card("あ", CardKind.Reading, Phase.Review, now + 5000)
      )
      assert(next(cards, introduced = 3).contains(SessionExercise.Writing("あ")))
    }
    test(
      "once nothing else is left, the cards being learned before they are due"
    ) {
      val cards = List(
        card("あ", CardKind.Writing, Phase.Learning, now + 8 * 60 * 1000),
        card("い", CardKind.Writing, Phase.Learning, now + 9 * 60 * 1000),
        card("あ", CardKind.Reading, Phase.Review, now + 5000),
        card("い", CardKind.Reading, Phase.Review, now + 5000)
      )
      assert(next(cards, introduced = 3).contains(SessionExercise.Writing("あ")))
      assert(
        next(cards, introduced = 3, last = Some(SessionExercise.Writing("あ")))
          .contains(SessionExercise.Writing("い"))
      )
      val reviewed = cards.map(_.copy(phase = Phase.Review))
      assert(next(reviewed, introduced = 3).isEmpty)
    }
    test("nothing once the time is up") {
      val cards = List(card("あ", CardKind.Reading, Phase.Learning, now - 10))
      assert(next(cards, at = endsAt).isEmpty)
    }
  }
}
