package hanamaru.backend.writing

import hanamaru.backend.reference.{KanjiVg, KanjiVgArchive}
import hanamaru.backend.storage.Places
import hanamaru.shared.kana.{Kana, Script}
import hanamaru.shared.writing.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Random

import com.github.plokhotnyuk.jsoniter_scala.core.readFromString
import utest.*

/** The kana recogniser: each kana written as its model, a little shaken, is
  * recognised as itself, or as its twin in the other script (へ and ヘ). Needs
  * KanjiVG in the cache. The characters the learner wrote from memory, when the
  * application has kept some, are recognised too, and how well is printed: the
  * material the recogniser is tuned on.
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
    test("the kana the learner wrote from memory, recognised") {
      (strokes, Some(places.writingAttempts).filter(Files.exists(_))) match {
        case (Some(kana), Some(file)) =>
          // each recognised among the kana of its script, as the field is
          val recognisers = Script.values.toList
            .map(script => script -> CharacterRecognizer.ofKana(kana, script))
            .toMap
          val ranked = Files
            .readAllLines(file)
            .asScala
            .map(readFromString[WritingAttempt](_))
            .filter(_.step == WritingStep.FromMemory)
            .toList
            .flatMap(attempt =>
              scriptOf(attempt.character).map(script =>
                attempt -> recognisers(script).ranked(
                  attempt.strokes.map(_.points.map(_.point))
                )
              )
            )
          // those the matcher judged right: the others are often another kana
          val wellWritten = ranked.filter((attempt, _) =>
            WritingCorrection.outcome(attempt) == WritingOutcome.Right
          )
          def within(count: Int) = wellWritten.count((attempt, candidates) =>
            candidates
              .take(count)
              .exists(candidate => same(candidate.character, attempt.character))
          )
          val missed = wellWritten.collect {
            case (attempt, best :: _)
                if !same(best.character, attempt.character) =>
              s"${attempt.character}→${best.character}"
          }
          println(
            s"RECOGNISED ${wellWritten.size} kana written right from memory: first ${within(1)}, among three ${within(3)}; missed ${missed.mkString(" ")}"
          )
        case _ =>
          println("No kana written from memory kept: recognition not measured")
      }
    }
    test("the kana the learner wrote in the pad, recognised") {
      (strokes, Some(places.padCharacters).filter(Files.exists(_))) match {
        case (Some(kana), Some(file)) =>
          val recognisers = Script.values.toList
            .map(script => script -> CharacterRecognizer.ofKana(kana, script))
            .toMap
          val ranked = Files
            .readAllLines(file)
            .asScala
            .map(readFromString[PadCharacter](_))
            .toList
            .flatMap(written =>
              scriptOf(written.character).map(script =>
                written -> recognisers(script)
                  .ranked(written.strokes.map(_.map(_.point)))
                  .filterNot(candidate => Kana.isSmall(candidate.character))
              )
            )
          def within(count: Int) = ranked.count((written, candidates) =>
            candidates.take(count).exists(_.character == written.character)
          )
          val missed = ranked.collect {
            case (written, best :: _) if best.character != written.character =>
              s"${written.character}→${best.character}"
          }
          println(
            s"IN THE PAD ${ranked.size} kana: first ${within(1)}, among three ${within(3)}; missed ${missed.mkString(" ")}"
          )
        case _ =>
          println("No kana written in the pad kept: recognition not measured")
      }
    }
  }
}
