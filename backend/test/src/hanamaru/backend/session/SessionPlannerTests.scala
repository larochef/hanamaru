package hanamaru.backend.session

import hanamaru.backend.learner.{CardKind, KanaCard, Phase}
import hanamaru.shared.kana.{Kana, Script}
import hanamaru.shared.session.SessionExercise

import scala.util.Random

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
      at: Long = now,
      asked: Set[(String, CardKind)] = Set.empty,
      seed: Long = 1
  ) = SessionPlanner.next(
    cards,
    at,
    endsAt,
    stylus,
    introduced,
    3,
    last,
    asked = asked,
    random = Random(seed)
  )

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
      // no new kana: the cards met, before they are due
      val ofあ =
        Set(SessionExercise.Writing("あ"), SessionExercise.Reading("あ"))
      assert(next(met, introduced = 3).exists(ofあ))
      assert(next(met, at = endsAt - 60 * 1000).exists(ofあ))
    }
    test("nothing due: a card all the same rather than ending early") {
      val cards = List(
        card("あ", CardKind.Writing, Phase.Learning, now + 30 * 1000),
        card("あ", CardKind.Reading, Phase.Review, now + 5000)
      )
      assert(next(cards, introduced = 3).isDefined)
    }
    test(
      "nothing due: any card at random, those the session has not asked first"
    ) {
      // a few being learned, due in minutes, and the others reviewed long ago
      val cards = List(
        card("あ", CardKind.Reading, Phase.Learning, now + 8 * 60 * 1000),
        card("い", CardKind.Reading, Phase.Learning, now + 9 * 60 * 1000)
      ) ++ List("う", "え", "お", "か").map(character =>
        card(character, CardKind.Reading, Phase.Review, now + 3600000)
      )
      val seeds = (1L to 200L).toList
      // every card comes up, not only those being learned
      val drawn =
        seeds.flatMap(seed => next(cards, introduced = 3, seed = seed))
      assert(drawn.toSet.size == cards.size)
      // those the session has asked wait for the others
      val asked = cards
        .filter(_.character != "お")
        .map(card => card.character -> card.kind)
      assert(
        seeds.forall(seed =>
          next(cards, introduced = 3, asked = asked.toSet, seed = seed)
            .contains(SessionExercise.Reading("お"))
        )
      )
      // all asked: any, but not the one just asked
      val all = cards.map(card => card.character -> card.kind).toSet
      assert(
        seeds.forall(seed =>
          !next(
            cards,
            introduced = 3,
            asked = all,
            last = Some(SessionExercise.Reading("お")),
            seed = seed
          ).contains(SessionExercise.Reading("お"))
        )
      )
    }
    test("a katakana session: only katakana, due or new") {
      val katakana = Kana.all.filter(_.script == Script.Katakana)
      val cards = List(card("あ", CardKind.Reading, Phase.Learning, now - 10))
      // the hiragana due is not asked; the first katakana is new
      assert(
        SessionPlanner
          .next(cards, now, endsAt, true, 0, 3, None, katakana)
          .contains(SessionExercise.NewKana("ア", true))
      )
      val due = card("カ", CardKind.Reading, Phase.Review, now - 10) :: cards
      assert(
        SessionPlanner
          .next(due, now, endsAt, true, 0, 3, None, katakana)
          .contains(SessionExercise.Reading("カ"))
      )
    }
    test("nothing once the time is up") {
      val cards = List(card("あ", CardKind.Reading, Phase.Learning, now - 10))
      assert(next(cards, at = endsAt).isEmpty)
    }
  }
}
