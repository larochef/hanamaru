package hanamaru.backend.lessons

import hanamaru.backend.reference.Token

import utest.*

object SentenceGrammarTests extends TestSuite {
  private def token(surface: String, form: String, pos: String*) =
    Token(surface, form, surface, pos.toList)

  private val points = Curriculum.points
  private def upTo(id: String) = points.indexWhere(_.id == id)

  // 田中さんは学校へ行きます。
  private val going = List(
    token("田中", "田中", "名詞", "固有名詞", "人名", "姓"),
    token("さん", "さん", "接尾辞", "名詞的", "一般"),
    token("は", "は", "助詞", "係助詞"),
    token("学校", "学校", "名詞", "普通名詞", "一般"),
    token("へ", "へ", "助詞", "格助詞"),
    token("行き", "行く", "動詞", "非自立可能", "*", "*", "五段-カ行", "連用形-一般"),
    token("ます", "ます", "助動詞", "*", "*", "*", "助動詞-マス", "終止形-一般"),
    token("。", "。", "補助記号", "句点")
  )

  val tests: Tests = Tests {
    test("the points shown, a name and a suffix let through") {
      val demands = SentenceGrammar.demands(going, upTo("de"), Set("学校", "行く"))
      assert(
        demands.points == List("wa", "masu", "ni-he"),
        demands.problems.isEmpty
      )
    }
    test("a point not learned yet is named by what is written") {
      val demands = SentenceGrammar.demands(going, upTo("wo"), Set("学校", "行く"))
      assert(demands.later == List("ni-he"), demands.laterGrammar == List("へ"))
      assert(demands.problems == List("grammar not learned yet: へ"))
    }
    test("a particle no point covers, and a word not known") {
      // 学校が大きい
      val tokens = List(
        token("学校", "学校", "名詞", "普通名詞", "一般"),
        token("が", "が", "助詞", "格助詞"),
        token("大きい", "大きい", "形容詞", "一般", "*", "*", "形容詞", "終止形-一般")
      )
      val demands = SentenceGrammar.demands(tokens, upTo("de"), Set("学校"))
      assert(
        demands.uncovered == List("が"),
        demands.unknownWords == List("大きい")
      )
    }
  }
}
