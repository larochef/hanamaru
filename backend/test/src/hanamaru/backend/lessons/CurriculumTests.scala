package hanamaru.backend.lessons

import hanamaru.backend.placement.GrammarPlacementTest
import hanamaru.backend.reference.{Analyser, Token}
import hanamaru.backend.storage.Places

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

import utest.*

object CurriculumTests extends TestSuite {
  private val points = Curriculum.points

  // the analyser's dictionary where the application keeps it, once it has been
  // started: without it, the examples cannot be analysed here
  private lazy val analyser: Option[Analyser] = {
    val places = Places.fromEnvironment(
      System.getenv().asScala.toMap,
      Path.of(System.getProperty("user.home"))
    )
    Some(Analyser.dictionaryIn(places.reference))
      .filter(Files.exists(_))
      .map(Analyser(_))
  }

  private def token(form: String, pos: String*) =
    Token(form, form, form, pos.toList)

  val tests: Tests = Tests {
    test("the points are named once, and rely only on points before them") {
      assert(points.map(_.id).distinct.size == points.size)
      points.zipWithIndex.foreach { (point, index) =>
        val before = points.take(index).map(_.id).toSet
        assert(point.requires.forall(before))
        assert(
          point.examples.nonEmpty,
          point.explanation.nonEmpty,
          point.signature.nonEmpty
        )
      }
    }
    test("a signature marks the tokens of the sequence it finds") {
      val negative = points.find(_.id == "masen-mashita").get
      val tokens = List(
        token("食べる", "動詞", "一般", "*", "*", "下一段-バ行", "未然形-一般"),
        token("ます", "助動詞", "*", "*", "*", "助動詞-マス", "未然形-一般"),
        token("ぬ", "助動詞", "*", "*", "*", "助動詞-ヌ", "終止形-撥音便")
      )
      assert(negative.shownIn(tokens) == Set(1, 2))
      assert(negative.shownIn(tokens.take(2)).isEmpty)
      val masu = points.find(_.id == "masu").get
      // the present's signature asks for ます as it ends a sentence
      assert(masu.shownIn(tokens).isEmpty)
    }
    test(
      "each placement question: one blank, its answer among distinct choices"
    ) {
      points.foreach { point =>
        assert(point.placement.size == 2)
        point.placement.foreach { item =>
          assert(
            item.sentence.count(_ == '＿') == 1,
            item.choices.contains(item.answer)
          )
          assert(item.choices.distinct.size == item.choices.size)
        }
      }
    }
    test(
      "each placement question, answered, shows its point and none after it; its blank is whole tokens"
    ) {
      analyser.foreach { analysis =>
        points.zipWithIndex.foreach { (point, index) =>
          point.placement.foreach { item =>
            val tokens = analysis.tokens(item.filled)
            assert(point.shownIn(tokens).nonEmpty, item.filled.nonEmpty)
            assert(
              points.drop(index + 1).forall(_.shownIn(tokens).isEmpty),
              item.filled.nonEmpty
            )
            val blank =
              GrammarPlacementTest.inBlank(tokens.map(_.surface), item)
            assert(
              blank.toList.sorted
                .map(tokens(_).surface)
                .mkString == item.answer,
              item.filled.nonEmpty
            )
          }
        }
      }
    }
    test("each example shows its point, and none that comes after it") {
      analyser match {
        case None =>
          println(
            "Examples not analysed: the analyser's dictionary is not in the cache"
          )
        case Some(analysis) =>
          points.zipWithIndex.foreach { (point, index) =>
            point.examples.foreach { example =>
              val tokens = analysis.tokens(example.japanese)
              assert(point.shownIn(tokens).nonEmpty, example.japanese.nonEmpty)
              val later = points
                .drop(index + 1)
                .filter(_.shownIn(tokens).nonEmpty)
                .map(_.id)
              assert(later.isEmpty, example.japanese.nonEmpty)
            }
          }
      }
    }
  }
}
