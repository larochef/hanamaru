package hanamaru.backend.reference

import utest.*

object SentenceLayoutTests extends TestSuite {
  private def token(
      surface: String,
      form: String,
      reading: String,
      pos: String*
  ) =
    Token(surface, form, reading, pos.toList)

  private def shown(tokens: List[Token]) =
    SentenceLayout
      .laidOut(tokens, _ => false)
      .map(part => (if (part.spaceBefore) " " else "") + part.text)
      .mkString

  val tests: Tests = Tests {
    test(
      "words in kana, spaced; particles and auxiliaries with the word before"
    ) {
      // 私は日本語を勉強します。
      val tokens = List(
        token("私", "私", "わたくし", "代名詞"),
        token("は", "は", "は", "助詞", "係助詞"),
        token("日本", "日本", "にっぽん", "名詞", "固有名詞"),
        token("を", "を", "を", "助詞", "格助詞"),
        token("勉強", "勉強", "べんきょう", "名詞", "普通名詞", "サ変可能"),
        token("し", "する", "し", "動詞", "非自立可能"),
        token("ます", "ます", "ます", "助動詞"),
        token("。", "。", "。", "補助記号", "句点")
      )
      // the everyday readings of 私 and 日本; します kept with its noun
      assert(shown(tokens) == "わたしは にほんを べんきょうします。")
    }
    test(
      "a prefix and a comma keep the next word; katakana as written; じゃありません whole"
    ) {
      val tokens = List(
        token("はい", "はい", "はい", "感動詞"),
        token("、", "、", "、", "補助記号", "読点"),
        token("お", "お", "お", "接頭辞"),
        token("茶", "茶", "ちゃ", "名詞", "普通名詞"),
        token("と", "と", "と", "助詞", "格助詞"),
        token("パン", "パン", "ぱん", "名詞", "普通名詞"),
        token("じゃ", "だ", "じゃ", "助動詞"),
        token("あり", "ある", "あり", "動詞", "非自立可能"),
        token("ませ", "ます", "ませ", "助動詞"),
        token("ん", "ぬ", "ん", "助動詞")
      )
      assert(shown(tokens) == "はい、おちゃと パンじゃありません")
    }
    test("a counter kept with its number") {
      val tokens = List(
        token("七", "七", "しち", "名詞", "数詞"),
        token("時", "時", "じ", "名詞", "普通名詞", "助数詞可能"),
        token("に", "に", "に", "助詞", "格助詞"),
        token("起き", "起きる", "おき", "動詞", "一般")
      )
      assert(shown(tokens) == "しちじに おき")
    }
    test("何 read なに or なん by the sound after it") {
      val what = token("何", "何", "なん", "代名詞")
      val particle = token("を", "を", "を", "助詞", "格助詞")
      val copula = token("です", "です", "です", "助動詞")
      assert(
        shown(List(what, particle)) == "なにを",
        shown(List(what, copula)) == "なんです"
      )
    }
    test("ではありません whole") {
      val tokens = List(
        token("学生", "学生", "がくせい", "名詞", "普通名詞"),
        token("で", "で", "で", "助詞", "格助詞"),
        token("は", "は", "は", "助詞", "係助詞"),
        token("あり", "ある", "あり", "動詞", "非自立可能"),
        token("ませ", "ます", "ませ", "助動詞"),
        token("ん", "ぬ", "ん", "助動詞")
      )
      assert(shown(tokens) == "がくせいではありません")
    }
    test("a reading given replaces the analyser's; marked tokens by index") {
      val tokens =
        List(token("何", "何", "なん", "代名詞"), token("を", "を", "を", "助詞", "格助詞"))
      val parts = SentenceLayout.laidOut(
        tokens,
        _ == 1,
        token => Option.when(token.surface == "何")("なに")
      )
      assert(
        parts.map(_.text) == List("なに", "を"),
        parts.map(_.marked) == List(false, true)
      )
    }
  }
}
