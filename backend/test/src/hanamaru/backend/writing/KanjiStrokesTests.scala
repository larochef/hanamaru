package hanamaru.backend.writing

import hanamaru.backend.reference.{KanjiBook, KanjiVg, KanjiVgArchive}
import hanamaru.backend.storage.{Database, Places}
import hanamaru.shared.writing.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Random

import utest.*

/** The stroke matcher, tuned on kana, on the school kanji: their own strokes, a
  * little shaken as a hand would, must be found right; two strokes swapped must
  * not. Needs KanjiVG and the kanji in the cache, where the application keeps
  * them once started.
  */
object KanjiStrokesTests extends TestSuite {

  private lazy val data: Option[(KanjiVgArchive, KanjiBook)] = {
    val places = Places.fromEnvironment(
      System.getenv().asScala.toMap,
      Path.of(System.getProperty("user.home"))
    )
    val archive = KanjiVg.archiveIn(places.reference)
    val kanji = places.reference.resolve("kanji.db")
    Option.when(Files.exists(archive) && Files.exists(kanji))(
      (KanjiVgArchive(archive), KanjiBook(Database.connect(kanji)))
    )
  }

  // a stroke drawn by hand: its points, each moved a little
  private def drawn(stroke: List[Point], random: Random, shake: Double) =
    stroke.zipWithIndex.map((point, index) =>
      DrawnPoint(
        point.x + random.nextGaussian() * shake,
        point.y + random.nextGaussian() * shake,
        0.5,
        index * 10.0
      )
    )

  val tests: Tests = Tests {
    test(
      "the school kanji, written as their model, a little shaken, are right; two strokes swapped are not"
    ) {
      data match {
        case None =>
          println(
            "Kanji strokes not checked: KanjiVG or the kanji are not in the cache"
          )
        case Some((archive, book)) =>
          val random = Random(7)
          val kanji = (1 to 6).flatMap(book.ofGrade).map(_.literal)
          val judged = kanji.flatMap(literal =>
            archive.strokes(literal).map { strokes =>
              val model =
                strokes.strokes.map(stroke => SvgPath.points(stroke.path))
              val right = WritingCorrection.outcome(
                WritingCorrection
                  .attempt(literal, model.map(drawn(_, random, 1.5)), model)
              )
              val swapped = Option.when(model.size >= 2)(
                WritingCorrection.outcome(
                  WritingCorrection.attempt(
                    literal,
                    (model(1) :: model.head :: model.drop(2))
                      .map(drawn(_, random, 1.5)),
                    model
                  )
                )
              )
              (literal, right, swapped)
            }
          )
          val notRight = judged.collect {
            case (literal, outcome, _) if outcome != WritingOutcome.Right =>
              literal
          }
          val swapMissed = judged.collect {
            case (literal, _, Some(WritingOutcome.Right)) => literal
          }
          println(
            s"KANJI ${judged.size} checked; not right as written: ${notRight.size} ${notRight.mkString}; swap not seen: ${swapMissed.size} ${swapMissed.mkString}"
          )
          assert(judged.size > 1000, notRight.isEmpty)
          // two strokes alike, first and second, may be swapped unseen: 妹, 然, 属
          assert(swapMissed.size <= 5)
      }
    }
  }
}
