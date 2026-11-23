package hanamaru.backend.vocabulary

import hanamaru.backend.reference.Token
import hanamaru.shared.dictionary.{DictionaryWord, Meaning}
import hanamaru.shared.text.SentencePart

import utest.*

object SentenceCheckTests extends TestSuite {
  private def word(id: Long, writing: String, reading: String*) =
    DictionaryWord(
      id,
      5,
      List(writing),
      reading.toList,
      usuallyKana = false,
      List(Meaning("fre", Nil, List("x")))
    )

  private val cat = word(1, "猫", "ねこ")
  private val me = word(2, "私", "わたし", "わたくし")
  private val like = word(3, "好き", "すき")
  private val go = word(4, "行く", "いく", "ゆく")
  private val school = word(5, "学校", "がっこう")

  // 私は猫が好きです。 as Sudachi splits it
  private val sentence = List(
    Token("私", "私", "わたくし", List("代名詞", "*")),
    Token("は", "は", "は", List("助詞", "係助詞")),
    Token("猫", "猫", "ねこ", List("名詞", "普通名詞")),
    Token("が", "が", "が", List("助詞", "格助詞")),
    Token("好き", "好き", "すき", List("形状詞", "一般")),
    Token("です", "です", "です", List("助動詞", "*")),
    Token("。", "。", "。", List("補助記号", "句点"))
  )

  val tests: Tests = Tests {
    test("known words only: laid out in kana, spaced, the word asked blanked") {
      val parts =
        SentenceCheck.checked(sentence, cat, List(me, like)).toOption.get
      assert(parts.map(_.text).mkString == "わたしはねこがすきです。")
      assert(parts.filter(_.marked).map(_.text) == List("ねこ"))
      assert(parts.filter(_.spaceBefore).map(_.text) == List("ねこ", "すき"))
      // the usual reading of a word used as it stands: わたし, not わたくし
      assert(
        parts.head == SentencePart("わたし", marked = false, spaceBefore = false)
      )
    }
    test("a word the learner does not know is named") {
      assert(SentenceCheck.checked(sentence, cat, List(me)) == Left(List("好き")))
    }
    test("the word asked must be in it") {
      val problems = SentenceCheck
        .checked(sentence, school, List(me, cat, like))
        .left
        .toOption
        .get
      assert(problems.exists(_.contains("missing")))
    }
    test(
      "a conjugated word is matched by its dictionary form; する and numbers are grammar"
    ) {
      val going = List(
        Token("学校", "学校", "がっこう", List("名詞", "普通名詞")),
        Token("に", "に", "に", List("助詞", "格助詞")),
        Token("行き", "行く", "いき", List("動詞", "非自立可能")),
        Token("ます", "ます", "ます", List("助動詞", "*")),
        Token("三", "三", "さん", List("名詞", "数詞")),
        Token("し", "する", "し", List("動詞", "非自立可能"))
      )
      val parts = SentenceCheck.checked(going, go, List(school)).toOption.get
      // conjugated, it is shown as read: the blank stands for the dictionary form asked
      assert(parts.filter(_.marked).map(_.text) == List("いき"))
    }
  }
}
