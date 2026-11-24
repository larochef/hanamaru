package hanamaru.backend.lessons

import hanamaru.backend.learner.{CardKind, ItemKind, LearnerStore}
import hanamaru.backend.reference.{Dictionary, KanjiBook}
import hanamaru.backend.storage.{Database, Places}
import hanamaru.shared.dictionary.{DictionaryWord, Meaning}
import hanamaru.shared.learner.Grade

import java.nio.file.{Files, Path}
import java.time.Instant
import scala.jdk.CollectionConverters.*

import utest.*

object UnitsTests extends TestSuite {

  // no kanji taught: the units' kanji left out
  private val noKanji: () => Option[String => Option[Int]] =
    () => Some(_ => None)

  // the dictionary where the application keeps it, once it has been started
  private lazy val dictionary: Option[Dictionary] = {
    val places = Places.fromEnvironment(
      System.getenv().asScala.toMap,
      Path.of(System.getProperty("user.home"))
    )
    Some(places.reference.resolve("dictionary.db"))
      .filter(Files.exists(_))
      .map(file => Dictionary(Database.connect(file)))
  }

  // the kanji where the application keeps them, once it has been started
  private lazy val kanjiBook: Option[KanjiBook] = {
    val places = Places.fromEnvironment(
      System.getenv().asScala.toMap,
      Path.of(System.getProperty("user.home"))
    )
    Some(places.reference.resolve("kanji.db"))
      .filter(Files.exists(_))
      .map(file => KanjiBook(Database.connect(file)))
  }

  private def entry(
      id: Long,
      level: Int,
      writings: List[String],
      readings: String*
  ) =
    DictionaryWord(
      id,
      level,
      writings,
      readings.toList,
      usuallyKana = false,
      List(Meaning("eng", Nil, List("x")))
    )

  val tests: Tests = Tests {
    test(
      "the entry meant: the reading asked for, the form as usually written, the core vocabulary's"
    ) {
      val top = entry(1, 0, List("上"), "かみ")
      val above = entry(2, 5, List("上"), "うえ")
      assert(Units.chosen(List(top, above), "上", Some("うえ")).contains(above))
      val rare = entry(3, 0, List("何"), "なに")
      val core = entry(4, 5, List("何"), "なに", "なん")
      assert(Units.chosen(List(rare, core), "何", None).contains(core))
      assert(Units.chosen(List(rare), "何", Some("いつ")).isEmpty)
    }
    test("every word of every unit is in the dictionary") {
      dictionary match {
        case None =>
          println(
            "Units' words not looked up: the dictionary is not in the cache"
          )
        case Some(words) =>
          val learner = LearnerStore(
            Database.open(
              Files.createTempDirectory("hanamaru-units").resolve("learner.db")
            )
          )
          val units =
            Units(Curriculum.points, () => Some(words), learner, noKanji)
          Curriculum.points.foreach { point =>
            val found = units.wordsOf(point)
            val missing = point.wordForms.filterNot(form =>
              found.exists(word =>
                word.writings.contains(form) || word.readings.contains(form)
              )
            )
            assert(missing.isEmpty, point.id.nonEmpty)
            println(
              s"UNIT ${point.id}: " + found
                .map(word =>
                  s"${word.writings.headOption.getOrElse("")}(${word.readings.head})"
                )
                .mkString(" ")
            )
          }
      }
    }
    test(
      "the next unit opens once its point is practised and most of its words are stable"
    ) {
      dictionary.foreach { words =>
        val learner = LearnerStore(
          Database.open(
            Files.createTempDirectory("hanamaru-units").resolve("learner.db")
          )
        )
        val units =
          Units(Curriculum.points, () => Some(words), learner, noKanji)
        val first = Curriculum.points.head
        assert(units.current.map(_._1.id).contains(first.id))
        val at = Instant.parse("2026-11-24T10:00:00Z")
        val unitWords = units.wordsOf(first)
        unitWords
          .take((unitWords.size * Units.acquiredShare).ceil.toInt)
          .foreach(word =>
            learner.reviewed(
              ItemKind.Word,
              word.id.toString,
              CardKind.Production,
              Grade.Easy,
              "",
              at
            )
          )
        // the words without the lesson: the unit is still under way
        assert(units.current.map(_._1.id).contains(first.id))
        learner.reviewed(
          ItemKind.Grammar,
          first.id,
          CardKind.Usage,
          Grade.Easy,
          "",
          at
        )
        assert(units.current.map(_._1.id).contains(Curriculum.points(1).id))
      }
    }
    test("a unit with kanji opens the next once they are read and written") {
      dictionary.foreach { words =>
        val learner = LearnerStore(
          Database.open(
            Files.createTempDirectory("hanamaru-units").resolve("learner.db")
          )
        )
        // 学 the only kanji taught: the first unit's (学生), carried on to the
        // first unit with kanji
        val units = Units(
          Curriculum.points,
          () => Some(words),
          learner,
          () => Some(literal => Option.when(literal == "学")(1))
        )
        val withKanji = Curriculum.points(Units.firstUnitWithKanji)
        assert(units.kanjiOf(Curriculum.points.head).contains(Nil))
        assert(units.kanjiOf(withKanji).contains(List("学")))
        val at = Instant.parse("2026-11-24T10:00:00Z")
        def reviewed(kind: ItemKind, key: String, card: CardKind) =
          learner.reviewed(kind, key, card, Grade.Easy, "", at)
        Curriculum.points.take(Units.firstUnitWithKanji + 1).foreach { point =>
          units
            .wordsOf(point)
            .foreach(word =>
              reviewed(ItemKind.Word, word.id.toString, CardKind.Production)
            )
          reviewed(ItemKind.Grammar, point.id, CardKind.Usage)
        }
        reviewed(ItemKind.Kanji, "学", CardKind.Reading)
        // read, not written yet: the unit is still under way
        assert(units.current.map(_._1.id).contains(withKanji.id))
        reviewed(ItemKind.Kanji, "学", CardKind.Writing)
        assert(
          units.current
            .map(_._1.id)
            .contains(Curriculum.points(Units.firstUnitWithKanji + 1).id)
        )
      }
    }
    test("the kanji spread across the units, a few each, every one once") {
      // every character of the units' words taken for a kanji of grade 1
      val byUnit = Units.kanjiByUnit(
        Curriculum.points,
        literal => Option.when(literal.head >= '\u4e00')(1)
      )
      val all = byUnit.flatMap(_._2)
      assert(byUnit.map(_._1) == Curriculum.points.map(_.id))
      assert(all.distinct == all)
      assert(byUnit.init.forall(_._2.size <= Units.kanjiPerUnit))
      // none in the first units; then the first unit's, as its words have them
      assert(byUnit.take(Units.firstUnitWithKanji).forall(_._2.isEmpty))
      assert(byUnit(Units.firstUnitWithKanji)._2.take(2) == List("学", "生"))
      val inWords = Curriculum.points
        .flatMap(_.wordForms)
        .flatMap(_.map(_.toString))
        .filter(_.head >= '\u4e00')
        .toSet
      assert(all.toSet == inWords)
    }
    test("the units teach every kanji of the first two grades") {
      kanjiBook match {
        case None =>
          println("Units' kanji not checked: the kanji are not in the cache")
        case Some(book) =>
          val taught = Units
            .kanjiByUnit(
              Curriculum.points,
              literal => book.kanji(literal).flatMap(_.grade)
            )
            .flatMap(_._2)
          val grades = (1 to Units.kanjiGrades).flatMap(book.ofGrade)
          val missing = grades.map(_.literal).filterNot(taught.contains)
          assert(missing.isEmpty, taught.size == grades.size)
      }
    }
  }
}
