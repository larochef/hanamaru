package hanamaru.backend.vocabulary

import hanamaru.backend.kanji.KanjiHand

import hanamaru.backend.lessons.Units
import hanamaru.backend.learner.*
import hanamaru.backend.models.*
import hanamaru.backend.reference.SampleJmdict
import hanamaru.backend.storage.Database
import hanamaru.backend.writing.{WritingAttempts, WritingReview}
import hanamaru.shared.dictionary.DictionaryWord
import hanamaru.shared.learner.Grade
import hanamaru.shared.vocabulary.*

import java.net.http.HttpClient
import java.nio.file.{Files, Path}
import java.time.{Duration, Instant}

import utest.*

object VocabularyPracticeTests extends TestSuite {
  private val start = Instant.parse("2026-01-01T19:00:00Z")

  /** A practice over the dictionary already imported in `reference`, with a
    * tutor that is not set up.
    */
  private def withPractice[A](
      test: (VocabularyPractice, LearnerStore) => A
  ): A = {
    val directory = Files.createTempDirectory("hanamaru-vocabulary")
    val database = Database.open(directory.resolve("learner.db"))
    val learner = LearnerStore(database)
    val http = HttpClient.newHttpClient()
    val dictionary = SampleJmdict.dictionaryIn(directory)
    val tutor = LlamaServer(
      LocalSettings(Map.empty),
      Path.of("/none"),
      HuggingFaceCache(directory),
      directory,
      http
    )
    try
      test(
        VocabularyPractice(
          () => Some(dictionary),
          learner,
          MeaningJudge(LlamaClient(tutor, http)),
          WritingAttempts(directory.resolve("attempts.jsonl")),
          WritingReview(learner),
          SentenceWriter(
            LlamaClient(tutor, http),
            () => None,
            Refusals(database, "none")
          ),
          Units(Nil, () => Some(dictionary), learner, () => Some(_ => None)),
          KanjiHand.of(() => None, learner)
        ),
        learner
      )
    finally database.close()
  }

  val tests: Tests = Tests {
    test(
      "a series planned: new words shown, every word asked both ways, new ones again"
    ) {
      withPractice { (practice, _) =>
        val words = practice.series(start, SeriesOf.MyLesson).toOption.get
        val tasks = VocabularyPractice.planned(words)
        val fresh = words.filter(_.isNew)
        assert(
          tasks.take(fresh.size) == fresh.map(SeriesTask.Intro(_)),
          tasks.count {
            case SeriesTask.Ask(_, Direction.Production, false, 0) => true
            case _                                                 => false
          } == words.size,
          tasks.count {
            case SeriesTask.Ask(_, Direction.Meaning, false, 0) => true
            case _                                              => false
          } == words.size,
          tasks.drop(fresh.size + 2 * words.size).forall {
            case SeriesTask.Ask(word, Direction.Production, true, 0) =>
              word.isNew
            case _ => false
          }
        )
      }
    }
    test("a question missed comes back a few questions later, twice at most") {
      assert(
        VocabularyPractice.comesBack(missed = true, attempt = 0).contains(4),
        VocabularyPractice.comesBack(missed = true, attempt = 1).contains(4),
        VocabularyPractice.comesBack(missed = true, attempt = 2).isEmpty,
        VocabularyPractice.comesBack(missed = false, attempt = 0).isEmpty
      )
    }
    test("a first series: new words in the order they are learned") {
      withPractice { (practice, _) =>
        val series = practice.series(start, SeriesOf.MyLesson).toOption.get
        assert(series.map(_.word.id) == List(2000002L, 2000001L, 2000003L))
        assert(series.forall(_.isNew))
      }
    }
    test(
      "answers judged and kept as reviews; the words met come back when due"
    ) {
      withPractice { (practice, learner) =>
        def answer(
            direction: Direction,
            typed: String,
            known: Boolean = false
        ) =
          practice
            .answered(
              VocabularyAnswer(2000002L, direction, typed, 1000, known),
              start
            )
            .toOption
            .get
        assert(
          answer(Direction.Production, "neko") == VocabularyVerdict(
            Verdict.Right,
            Some(Grade.Good),
            None
          )
        )
        assert(answer(Direction.Meaning, "Chat").verdict == Verdict.Right)
        // not found among the meanings, no tutor: the learner is to say
        val unsure = answer(Direction.Meaning, "félin")
        assert(unsure == VocabularyVerdict(Verdict.Unsure, None, None))
        practice.selfGraded(
          SelfGrade(2000002L, Direction.Meaning, "félin", right = true),
          start
        )
        assert(learner.metKeys(ItemKind.Word) == Set("2000002"))
        // the word met is not new any more; due again, it comes first
        val later =
          practice
            .series(start.plus(Duration.ofDays(30)), SeriesOf.MyLesson)
            .toOption
            .get
        assert(
          later.head == later.head.copy(isNew = false),
          later.head.word.id == 2000002L
        )
      }
    }
    test(
      "nothing due, nothing new: a series asked for is filled with words met"
    ) {
      withPractice { (practice, _) =>
        val all = List(2000001L, 2000002L, 2000003L)
        all.foreach(id =>
          practice.answered(
            VocabularyAnswer(id, Direction.Production, "?", 1000, true),
            start
          )
        )
        val soon = start.plus(Duration.ofSeconds(30))
        // My lesson takes only what is due
        assert(practice.series(soon, SeriesOf.MyLesson).toOption.get.isEmpty)
        val free = practice.series(soon, SeriesOf.Free).toOption.get
        assert(free.map(_.word.id).sorted == all, free.forall(!_.isNew))
      }
    }
    test("a new word said to be known and answered right is to confirm") {
      withPractice { (practice, learner) =>
        val verdict = practice
          .answered(
            VocabularyAnswer(
              2000001L,
              Direction.Production,
              "kirei",
              900,
              true
            ),
            start
          )
          .toOption
          .get
        assert(verdict.grade.contains(Grade.Easy))
        assert(learner.metKeys(ItemKind.Word) == Set("2000001"))
        assert(learner.dueKeys(ItemKind.Word, start.toEpochMilli, 10).isEmpty)
      }
    }

    test(
      "known words written in kanji: stable, their kanji all written, others in kana"
    ) {
      def word(id: Long, writing: String, reading: String, kana: Boolean) =
        DictionaryWord(id, 5, List(writing), List(reading), kana, Nil)
      val words = List(
        word(1, "会社", "かいしゃ", kana = false),
        word(2, "友達", "ともだち", kana = false),
        word(3, "学校", "がっこう", kana = false),
        word(4, "分かる", "わかる", kana = true),
        word(5, "先生", "せんせい", kana = false)
      )
      // 達 not taught: 友だち; 校 not written yet; 先生 not stable
      val shown = Map(1L -> "会社", 2L -> "友だち", 3L -> "学校", 5L -> "先生")
      val writable = VocabularyPractice.writableInKanji(
        words,
        _ != 5L,
        Set("会", "社", "友", "学", "先", "生", "分"),
        each => shown.getOrElse(each.id, "")
      )
      assert(writable.map(_._2) == List("会社", "友だち"))
    }
    test(
      "the words asked in kanji: those due first, a few new ones a day, none twice"
    ) {
      val writable = (1L to 8L).toList.map(id =>
        DictionaryWord(id, 5, List(s"字$id"), List("じ"), false, Nil) -> s"字$id"
      )
      val at = start.toEpochMilli
      def card(id: Long, due: Long, first: Long) =
        ItemCard(id.toString, CardKind.Writing, Phase.Review, due, 1, first)
      val none = VocabularyPractice.inKanji(Nil, writable, start, Set.empty)
      assert(
        none.map(_.word.id) == (1L to 5L).toList,
        none.forall(each => each.isNew && each.asked == WordAsked.InKanji)
      )
      // four met within the day, one due: one new left today
      val met = (1L to 4L).toList.map(id =>
        card(id, if (id == 3L) at - 1 else at + 1000, at - 1000)
      )
      val asked = VocabularyPractice.inKanji(met, writable, start, Set(5L))
      assert(
        asked.map(each => each.word.id -> each.isNew) == List(
          3L -> false,
          6L -> true
        )
      )
    }
    test("a word asked in kanji: only so, after the others, and graded apart") {
      withPractice { (practice, learner) =>
        val words = practice.series(start, SeriesOf.MyLesson).toOption.get
        val cat = words.find(_.word.id == 2000002L).get
        val inKanji = cat.copy(isNew = false, asked = WordAsked.InKanji)
        val tasks =
          VocabularyPractice.planned(words.filterNot(_ == cat) :+ inKanji)
        assert(
          tasks.collect {
            case SeriesTask.Ask(word, direction, _, _) if word == inKanji =>
              direction
          } == List(Direction.InKanji),
          tasks.last != SeriesTask.Ask(inKanji, Direction.Production)
        )
        def answer(typed: String) = practice
          .answered(
            VocabularyAnswer(2000002L, Direction.InKanji, typed, 1000, false),
            start
          )
          .toOption
          .get
          .verdict
        // in kana, right for the word, not in kanji
        assert(answer("ねこ") == Verdict.Wrong, answer("猫") == Verdict.Right)
        assert(
          learner.metKeys(ItemKind.WordInKanji) == Set("2000002"),
          learner.metKeys(ItemKind.Word).isEmpty
        )
      }
    }
  }
}
