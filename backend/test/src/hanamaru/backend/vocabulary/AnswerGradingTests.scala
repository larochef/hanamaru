package hanamaru.backend.vocabulary

import hanamaru.backend.kanji.{KanjiHand, KanjiHandTests}

import hanamaru.shared.dictionary.{DictionaryWord, Meaning}
import hanamaru.shared.vocabulary.Verdict
import hanamaru.shared.writing.*

import utest.*

object AnswerGradingTests extends TestSuite {

  private def word(
      id: Long,
      writings: List[String],
      readings: List[String],
      french: String*
  ) =
    DictionaryWord(
      id,
      5,
      writings,
      readings,
      usuallyKana = false,
      List(Meaning("fre", Nil, french.toList))
    )

  private val school = word(1, List("学校"), List("がっこう"), "école")
  private val television = word(2, Nil, List("テレビ"), "télévision")
  private val cut = word(3, List("切手"), List("きって"), "timbre")
  private val coin = word(4, List("硬貨"), List("こうか"), "pièce de monnaie")
  private val alsoSchool =
    word(5, List("学院"), List("がくいん"), "école", "institut")

  private def production(typed: String, asked: DictionaryWord) =
    AnswerGrading.production(
      typed,
      asked,
      text =>
        List(school, television, cut, coin, alsoSchool).filter(
          _.readings.contains(text)
        ),
      hand.ways(asked)
    )

  // 学 written, 校 not yet
  private val hand = KanjiHand(KanjiHandTests.forms, () => Set("学"))

  val tests: Tests = Tests {
    test("production: any form, in either kana script, typed in romaji") {
      assert(production("gakkou", school) == Verdict.Right)
      assert(production("学校", school) == Verdict.Right)
      assert(production("terebi", television) == Verdict.Right)
      assert(production(" kitte ", cut) == Verdict.Right)
      assert(production("学こう", school) == Verdict.Right)
    }
    test(
      "production: a long vowel, a small tsu or a voiced kana missed is nearly right"
    ) {
      assert(production("gakko", school) == Verdict.NearMiss)
      assert(production("kite", cut) == Verdict.NearMiss)
      assert(production("koka", coin) == Verdict.NearMiss)
    }
    test("production: another word of the same meaning, or a wrong word") {
      assert(production("gakuin", school) == Verdict.OtherWord("がくいん"))
      assert(production("terebi", school) == Verdict.Wrong)
      assert(production("", school) == Verdict.Wrong)
    }
    test("written with the stylus: every character, every stroke") {
      def written(character: String, verdicts: StrokeVerdict*) = WritingAttempt(
        character,
        WritingStep.FromMemory,
        verdicts.toList.map(verdict =>
          JudgedStroke(
            List(DrawnPoint(1, 1, 0.5, 0)),
            0,
            StrokeJudgement(verdict, 1)
          )
        ),
        completed = true,
        expectedStrokes = verdicts.size
      )
      val right = StrokeVerdict.Accepted
      assert(
        AnswerGrading.handwritten(
          List(written("テ", right), written("レ", right), written("ビ", right)),
          hand.ways(television)
        ) == Verdict.Right
      )
      // only a stroke's direction wrong: the word is known, its writing not yet
      assert(
        AnswerGrading.handwritten(
          List(
            written("が", right),
            written("っ", StrokeVerdict.WrongDirection),
            written("こ", right),
            written("う", right)
          ),
          hand.ways(school)
        ) == Verdict.NearMiss
      )
      assert(
        AnswerGrading.handwritten(
          List(written("が", StrokeVerdict.Misdrawn)),
          hand.ways(school)
        ) == Verdict.Wrong
      )
      assert(
        AnswerGrading.handwritten(
          List(written("て", right), written("れ", right), written("び", right)),
          hand.ways(television)
        ) == Verdict.Wrong
      )
      // a box whose kana looks like others: another word, its strokes aside
      assert(
        AnswerGrading.handwritten(
          List(
            written("テ", right),
            written("レ", right),
            written("ビ", right).copy(looksLike = List("ピ", "ヒ", "ド"))
          ),
          hand.ways(television)
        ) == Verdict.Wrong
      )
    }
    test(
      "written with the stylus: with kanji, all or some of them, or in kana"
    ) {
      def written(character: String) = WritingAttempt(
        character,
        WritingStep.FromMemory,
        List(
          JudgedStroke(
            List(DrawnPoint(1, 1, 0.5, 0)),
            0,
            StrokeJudgement(StrokeVerdict.Accepted, 1)
          )
        ),
        completed = true,
        expectedStrokes = 1
      )
      def verdict(characters: String*) = AnswerGrading.handwritten(
        characters.toList.map(written),
        hand.ways(school)
      )
      assert(
        verdict("学", "校") == Verdict.Right,
        verdict("学", "こ", "う") == Verdict.Right,
        verdict("が", "っ", "こ", "う") == Verdict.Right,
        verdict("学", "っ", "こ", "う") == Verdict.Wrong
      )
    }
    test("meaning: accents, case, articles and brackets do not count") {
      assert(AnswerGrading.meaning("Ecole", school))
      assert(AnswerGrading.meaning("l'école", school))
      assert(AnswerGrading.meaning("une pièce de monnaie", coin))
      assert(!AnswerGrading.meaning("monnaie", coin))
      val pretty = DictionaryWord(
        6,
        5,
        Nil,
        List("きれい"),
        usuallyKana = true,
        List(
          Meaning("eng", Nil, List("pretty", "clean")),
          Meaning("fre", Nil, List("joli (personne)"))
        )
      )
      assert(AnswerGrading.meaning("to be pretty", pretty))
      assert(AnswerGrading.meaning("jolie", pretty) == false)
      assert(AnswerGrading.meaning("joli", pretty))
    }
  }
}
