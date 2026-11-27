package hanamaru.backend.kanji

import hanamaru.shared.dictionary.DictionaryWord

import utest.*

object KanjiHandTests extends TestSuite {

  /** A few kanji's forms, for the tests that write words by hand. */
  val forms: String => List[String] = Map(
    "会" -> List("かい", "あ"),
    "社" -> List("しゃ", "じゃ"),
    "学" -> List("がく", "がっ"),
    "校" -> List("こう"),
    "先" -> List("せん", "さき"),
    "生" -> List("せい", "い")
  ).withDefaultValue(Nil)

  private def word(writing: String, reading: String, usuallyKana: Boolean) =
    DictionaryWord(1, 5, List(writing), List(reading), usuallyKana, Nil)

  val tests: Tests = Tests {
    val hand = KanjiHand(forms, () => Set("会"))
    test(
      "a word as the learner writes it: their kanji kept, the others in kana"
    ) {
      assert(
        hand.asWritten("会社", "かいしゃ", Set("会")).contains("会しゃ"),
        hand.asWritten("会社", "かいしゃ", Set("会", "社")).contains("会社"),
        hand.asWritten("会う", "あう", Set("会")).contains("会う"),
        hand.asWritten("先生", "せんせい", Set("会")).isEmpty,
        hand.asWritten(word("会社", "かいしゃ", true), Set("会")).isEmpty
      )
    }
    test("every way of writing a word: all kanji, all kana, and the mixes") {
      assert(
        hand.ways("会社", "かいしゃ") == Set("会社", "会しゃ", "かい社", "かいしゃ"),
        hand.ways("会う", "あう") == Set("会う", "あう"),
        // not laid out: only as written
        hand.ways("会社", "あいうえ") == Set("会社")
      )
    }
  }
}
