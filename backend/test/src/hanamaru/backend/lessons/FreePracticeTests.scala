package hanamaru.backend.lessons

import hanamaru.backend.reference.Token

import scala.util.Random

import utest.*

object FreePracticeTests extends TestSuite {
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
    test("every point learned drawn as often as the others, words once each") {
      val learned = points.take(4)
      val verbs = List(List("行く", "いく"), List("見る", "みる"))
      val nouns = (1 to 10).toList.map(each => List(s"名$each"))
      val targets =
        FreePractice.targets(learned, verbs, nouns, 8, Random(7))
      val drawn = targets.map(_.point.id)
      assert(
        targets.size == 8,
        learned.forall(point => drawn.count(_ == point.id) == 2),
        // each point once before any comes again
        drawn.take(4).toSet.size == 4
      )
      val words = targets.flatMap(_.words)
      assert(
        words.distinct == words,
        targets.take(2).forall(_.words.size == 2),
        targets.drop(2).forall(_.words.size == 1),
        words.count(verbs.contains) == 2
      )
    }
    test("the points that say verbs and adjectives are in the curriculum") {
      val ids = Curriculum.points.map(_.id).toSet
      assert(FreePractice.doingFrom.values.forall(ids))
    }
    test("nothing learned, nothing drawn") {
      assert(FreePractice.targets(Nil, Nil, Nil, 6, Random(1)).isEmpty)
    }
    test("a free sentence checked against its own target") {
      val toward = points.find(_.id == "ni-he").get
      val known = Set("学校", "行く")
      def problems(target: TextWriter.Target, at: String) =
        TextWriter.problems(target, going, upTo(at), known)
      assert(
        problems(
          TextWriter.Target(toward, List(List("行く", "いく"))),
          "de"
        ).isEmpty,
        problems(TextWriter.Target(toward, List(List("見る"))), "de") ==
          List("none of the words 見る is used"),
        problems(
          TextWriter.Target(points.find(_.id == "no").get, Nil),
          "de"
        ) == List("the grammar point 〜の〜 is not used"),
        problems(TextWriter.Target(toward, Nil), "wo") ==
          List("grammar not learned yet: へ")
      )
    }
  }
}
