package hanamaru.backend.lessons

import hanamaru.backend.kanji.{KanjiHand, KanjiHandTests}

import hanamaru.backend.models.*
import hanamaru.shared.lessons.*
import hanamaru.shared.text.{PartWord, SentencePart}

import java.net.http.HttpClient
import java.nio.file.{Files, Path}

import utest.*

object ExerciseJudgeTests extends TestSuite {

  // a tutor never set up: what the code cannot judge stays unjudged
  private val judge = {
    val directory = Files.createTempDirectory("hanamaru-exercises")
    val http = HttpClient.newHttpClient()
    val tutor = LlamaServer(
      LocalSettings(Map.empty),
      Path.of("/none"),
      HuggingFaceCache(directory),
      directory,
      http
    )
    ExerciseJudge(
      LlamaClient(tutor, http),
      () => None,
      KanjiHand(KanjiHandTests.forms, () => Set("会"))
    )
  }

  private def exercise(kind: ExerciseKind, expected: String) = LessonExercise(
    "wa",
    kind,
    List(
      SentencePart("わたし", false, false),
      SentencePart("は", true, false),
      SentencePart("がくせいです。", false, true)
    ),
    "私は学生です。",
    "Je suis étudiant.",
    List("は", "も", "の", "か"),
    expected
  )

  private val particle = exercise(ExerciseKind.Particle, "は")
  private val translation = exercise(ExerciseKind.Translation, "わたしはがくせいです。")

  private val form = exercise(ExerciseKind.Form, "ました")
  private val meaning = exercise(ExerciseKind.Meaning, "Je suis étudiant.")

  val tests: Tests = Tests {
    test("the form expected is right; another, no tutor to judge it, not") {
      assert(judge.judged(ExerciseAnswer(form, "ました")).right)
      assert(!judge.judged(ExerciseAnswer(form, "ます")).right)
    }
    test("a meaning as expected, whatever its case, accents and punctuation") {
      assert(judge.judged(ExerciseAnswer(meaning, "je suis etudiant")).right)
    }
    test("another meaning, the tutor away, is left to the learner") {
      val verdict = judge.judged(ExerciseAnswer(meaning, "Je suis un élève"))
      assert(!verdict.right, verdict.toSelfGrade)
      val none = judge.judged(ExerciseAnswer(meaning, " "))
      assert(!none.right, !none.toSelfGrade)
    }
    test("the particle expected is right, by the code") {
      assert(
        judge.judged(ExerciseAnswer(particle, "は")) == ExerciseVerdict(
          right = true,
          byTutor = false,
          None,
          None
        )
      )
    }
    test("particles typed in romaji or kana, each in its blank") {
      val two = particle.copy(
        parts = List(
          SentencePart("わたし", false, false),
          SentencePart("の", true, false),
          SentencePart("ちち", false, false),
          SentencePart("は", true, false),
          SentencePart("いしゃです。", false, true)
        ),
        expected = "の は"
      )
      assert(
        judge.judged(ExerciseAnswer(two, "no ha")).right,
        judge.judged(ExerciseAnswer(two, "の は")).right,
        !judge.judged(ExerciseAnswer(two, "no ")).right
      )
      // said as heard: wrong, its spelling explained
      val heard = judge.judged(ExerciseAnswer(two, "no wa"))
      assert(!heard.right, heard.explanation.exists(_.contains("ha, he, wo")))
    }
    test("another particle, with no tutor to judge it, is not right") {
      val verdict = judge.judged(ExerciseAnswer(particle, "も"))
      assert(!verdict.right, !verdict.byTutor)
    }
    test("a translation as expected, whatever its spaces and punctuation") {
      assert(
        judge.judged(ExerciseAnswer(translation, "わたしは がくせいです")) ==
          ExerciseVerdict(right = true, byTutor = false, None, None)
      )
    }
    test("a slip in a word: right for the lesson, the slip said") {
      def word(text: String) =
        SentencePart(text, false, false, Some(PartWord(text, text, "", "")))
      val listed = translation.copy(
        parts = List(
          word("わたし"),
          SentencePart("は", true, false),
          word("がくせい"),
          SentencePart("です", false, false),
          SentencePart("。", false, false)
        )
      )
      val slipped = judge.judged(ExerciseAnswer(listed, "わたしは がくせです"))
      assert(slipped.right, slipped.explanation.exists(_.contains("は")))
      assert(
        // the point itself missed
        !judge.judged(ExerciseAnswer(listed, "わたしが がくせいです")).right,
        // a slip in grammar beside it: です
        !judge.judged(ExerciseAnswer(listed, "わたしは がくせいだす")).right,
        // too many slips
        !judge.judged(ExerciseAnswer(listed, "わたは がくです")).right,
        // a word not as the dictionary has it: no slip allowed in it
        !judge.judged(ExerciseAnswer(translation, "わたしは がくせです")).right
      )
    }
    test("a slip within the nouns only, the analyser saying which") {
      def part(text: String, marked: Boolean = false) =
        SentencePart(text, marked, false)
      // 田中さんの車ですか: たなか, さん and くるま nouns
      val parts = List(
        part("たなか") -> true,
        part("さん") -> true,
        part("の", marked = true) -> false,
        part("くるま") -> true,
        part("です") -> false,
        part("か") -> false
      )
      assert(
        ExerciseJudge.pointRight("たなかさのくるまですか", parts),
        !ExerciseJudge.pointRight("たなかさんのくるまでしか", parts),
        !ExerciseJudge.pointRight("たなかさんはくるまですか", parts),
        ExerciseJudge.isNoun(List("接尾辞", "名詞的", "一般")),
        !ExerciseJudge.isNoun(List("助動詞"))
      )
    }
    test("in the other script, right, and the script said") {
      val verdict = judge.judged(ExerciseAnswer(translation, "ワタシハ ガクセイデス。"))
      assert(verdict.right, verdict.explanation.exists(_.contains("hiragana")))
      val bread = translation.copy(
        parts = List(
          SentencePart("パン", false, false),
          SentencePart("です", false, false)
        ),
        expected = "パンです"
      )
      val inHiragana = judge.judged(ExerciseAnswer(bread, "ぱんです"))
      assert(
        inHiragana.right,
        inHiragana.explanation.exists(_.contains("パン s'écrit en katakana"))
      )
    }
    test("は typed as it sounds is right, and said so") {
      val verdict = judge.judged(ExerciseAnswer(translation, "わたしわがくせいです"))
      assert(verdict.right, verdict.explanation.exists(_.contains("は")))
    }
    test("じゃありません for ではありません is right, the written form said") {
      val negation = translation.copy(expected = "がくせいではありません。")
      val verdict = judge.judged(ExerciseAnswer(negation, "がくせいじゃありません"))
      assert(verdict.right, verdict.explanation.exists(_.contains("ではありません")))
    }
    test(
      "written with kanji, all or some: right; in kana, the kanji recalled"
    ) {
      val company = translation.copy(
        parts = List(
          SentencePart("かいしゃ", false, false, written = Some("会社")),
          SentencePart("です", false, false)
        ),
        expected = "かいしゃです"
      )
      val inKana = judge.judged(ExerciseAnswer(company, "かいしゃです"))
      assert(
        judge.judged(ExerciseAnswer(company, "会社です")).right,
        judge.judged(ExerciseAnswer(company, "会しゃです")).right,
        inKana.right,
        inKana.explanation.exists(_.contains("会しゃ"))
      )
    }
    test("nothing typed is not right, and the tutor is not asked") {
      assert(
        judge.judged(ExerciseAnswer(translation, "")) == ExerciseVerdict(
          right = false,
          byTutor = false,
          None,
          None
        )
      )
    }
  }
}
