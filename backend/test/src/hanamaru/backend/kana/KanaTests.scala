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
    test("every character once") {
      assert(Kana.all.map(_.character).distinct.size == Kana.all.size)
    }
    test("katakana read as their hiragana") {
      assert(Kana.find("シ").map(_.romaji).contains("shi"))
      assert(Kana.find("ヲ").map(_.romaji).contains("wo"))
      assert(Kana.find("ッ").map(_.group).contains(KanaGroup.Small))
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
