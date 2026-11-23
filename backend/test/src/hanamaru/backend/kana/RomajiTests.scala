package hanamaru.backend.kana

import hanamaru.shared.kana.Romaji

import utest.*

object RomajiTests extends TestSuite {
  private def kana(typed: String) = Romaji.toKana(typed, finished = true)

  val tests: Tests = Tests {
    test("words as typed with an input method") {
      assert(kana("taberu") == "たべる")
      assert(kana("gakkou") == "がっこう")
      assert(kana("shinbun") == "しんぶん")
      assert(kana("konnichiha") == "こんにちは")
      assert(kana("kon'ya") == "こんや")
      assert(kana("kyou") == "きょう")
      assert(kana("matcha") == "まっちゃ")
      assert(kana("ra-men") == "らーめん")
      assert(kana("sensei") == "せんせい")
    }
    test("variants and small kana") {
      assert(kana("sitatu") == "したつ")
      assert(kana("hujisan") == "ふじさん")
      assert(kana("xtsu") == "っ")
      assert(kana("ltu") == "っ")
      assert(kana("fairu") == "ふぁいる")
      assert(kana("TABERU") == "たべる")
    }
    test("while typing: what may still become a kana is left as it is") {
      assert(Romaji.toKana("nihon") == "にほn")
      assert(Romaji.toKana("sh") == "sh")
      assert(Romaji.toKana("tab") == "たb")
      assert(kana("nihon") == "にほん")
      assert(Romaji.toKana("konn") == "こnn")
      assert(kana("konn") == "こん")
      // what the answer field does: only the romaji at its end is converted
      assert("こ" + Romaji.toKana("nni") == "こんに")
    }
    test("kana already typed are left as they are") {
      assert(kana("ここ") == "ここ")
      assert(kana("がっこう") == "がっこう")
      assert(kana("こkko") == "こっこ")
    }
    test("hiragana and katakana made alike") {
      assert(Romaji.asHiragana("テレビ") == "てれび")
      assert(Romaji.asHiragana("らーめん") == "らーめん")
    }
  }
}
