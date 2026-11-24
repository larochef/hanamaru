package hanamaru.backend.kana

import hanamaru.shared.kana.{Kana, KanaGroup, Script}

import utest.*

object KanaTests extends TestSuite {

  val tests: Tests = Tests {
    test("46 basic, 25 voiced and 4 small kana in each script") {
      for (script <- Script.values) {
        val kana = Kana.all.filter(_.script == script)
        assert(kana.count(_.group == KanaGroup.Basic) == 46)
        assert(kana.count(_.group == KanaGroup.Voiced) == 25)
        assert(kana.count(_.group == KanaGroup.Small) == 4)
      }
    }
    test("a kana made small, and back") {
      assert(
        Kana.smallOrLarge("や") == "ゃ",
        Kana.smallOrLarge("ゃ") == "や",
        Kana.smallOrLarge("ツ") == "ッ",
        Kana.smallOrLarge("か") == "か",
        Kana.isSmall("っ"),
        !Kana.isSmall("つ")
      )
    }
    test("a dakuten or a handakuten added, and taken away") {
      assert(
        Kana.withDakuten("か") == "が",
        Kana.withDakuten("が") == "か",
        Kana.withDakuten("ぱ") == "ば",
        Kana.withDakuten("ト") == "ド",
        Kana.withDakuten("な") == "な",
        Kana.withHandakuten("は") == "ぱ",
        Kana.withHandakuten("ぱ") == "は",
        Kana.withHandakuten("ば") == "ぱ",
        Kana.withHandakuten("か") == "か"
      )
    }
    test("every character once") {
      assert(Kana.all.map(_.character).distinct.size == Kana.all.size)
    }
    test("katakana read as their hiragana") {
      assert(Kana.find("シ").map(_.romaji).contains("shi"))
      assert(Kana.find("ヲ").map(_.romaji).contains("wo"))
      assert(Kana.find("ッ").map(_.group).contains(KanaGroup.Small))
    }
    test("ぢ and づ told apart from じ and ず, shown and typed") {
      def kana(character: String) = Kana.find(character).get
      assert(
        Kana.shownReading(kana("じ")) == "ji",
        Kana.shownReading(kana("ぢ")) == "ji (di)"
      )
      assert(
        Kana.shownReading(kana("ず")) == "zu",
        Kana.shownReading(kana("ヅ")) == "zu (du)"
      )
      assert(
        Kana.reads(kana("ぢ"), "di"),
        Kana.reads(kana("ぢ"), "ji"),
        !Kana.reads(kana("じ"), "di")
      )
      assert(
        Kana.reads(kana("づ"), "du"),
        !Kana.reads(kana("ず"), "du"),
        Kana.reads(kana("じ"), "zi")
      )
    }
    test("rows of five columns") {
      for (script <- Script.values) {
        for ((_, rows) <- Kana.chart(script)) {
          assert(rows.forall(_.size == 5))
        }
      }
    }
  }
}
