package hanamaru.backend.writing

import hanamaru.backend.reference.{KanjiVg, KanjiVgArchive}
import hanamaru.backend.storage.Places
import hanamaru.shared.kana.{Kana, Script}
import hanamaru.shared.writing.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Random

import utest.*

/** The kana recogniser: each kana written as its model, a little shaken, is
  * recognised as itself, or as its twin in the other script (へ and ヘ). Needs
  * KanjiVG in the cache. What the learner wrote is measured apart, in
  * `backend.calibration`.
  */
object KanaRecognitionTests extends TestSuite {

  private val places = Places.fromEnvironment(
    System.getenv().asScala.toMap,
    Path.of(System.getProperty("user.home"))
  )

  private lazy val strokes: Option[List[CharacterStrokes]] = {
    val archive = KanjiVg.archiveIn(places.reference)
    Option.when(Files.exists(archive)) {
      val kanjiVg = KanjiVgArchive(archive)
      CharacterRecognizer.kana.toList.flatMap(character =>
        kanjiVg.strokes(character.toString)
      )
    }
  }

  private def scriptOf(character: String): Option[Script] =
    if (character == "ー") Some(Script.Katakana)
    else Kana.find(character).map(_.script)

  // the same shape in both scripts
  private val twins = Set(Set("へ", "ヘ"), Set("べ", "ベ"), Set("ぺ", "ペ"))

  private def same(a: String, b: String) = a == b || twins(Set(a, b))

  val tests: Tests = Tests {
    test("each kana, written as its model, a little shaken, is recognised") {
      strokes match {
        case None =>
          println("Kana recognition not checked: KanjiVG is not in the cache")
        case Some(kana) =>
          val recognisers = Script.values.toList
            .map(script => script -> CharacterRecognizer.ofKana(kana, script))
            .toMap
          val random = Random(3)
          val wrong = kana.flatMap { model =>
            val character = model.character
            val shaken = model.strokes
              .map(stroke => SvgPath.points(stroke.path))
              .map(
                _.map(point =>
                  Point(
                    point.x + random.nextGaussian() * 1.5,
                    point.y + random.nextGaussian() * 1.5
                  )
                )
              )
            val best = recognisers(scriptOf(character).get).ranked(shaken).head
            Option.unless(
              same(best.character, character) &&
                best.distance <= CharacterRecognizer.recognisedWithin
            )(s"$character as ${best.character} (${best.distance})")
          }
          assert(kana.size > 150, wrong.isEmpty)
      }
    }
  }
}
