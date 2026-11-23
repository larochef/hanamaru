package hanamaru.backend.placement

import hanamaru.shared.kana.{Kana, Script}
import hanamaru.shared.placement.{PlacementQuestion, ScriptResult}

import scala.annotation.tailrec
import scala.util.Random

/** The kinds of answer a placement test keeps, as the database names them. */
enum AnswerKind(val name: String) {
  case Reading extends AnswerKind("reading")
  case Writing extends AnswerKind("writing")

  /** A script given up: its subject is the script's name (or `grammar`, the
    * grammar test given up).
    */
  case Skip extends AnswerKind("skip")

  /** A grammar question: its subject is the point and the question's number,
    * `wa#0`.
    */
  case Grammar extends AnswerKind("grammar")
}

/** An answer kept: its kind, what it was about, and whether it was right. */
final case class Answered(kind: AnswerKind, subject: String, correct: Boolean)

/** The kana placement test, as a function of the answers given so far: the next
  * question, and what each script showed.
  *
  * Hiragana, then katakana, row by row in the chart's order, from the easiest:
  * every kana of a row is read; a row is passed when at least half of it is
  * read right, and one of the kana read right is then written from memory. A
  * script stops after two rows failed in a row: a learner who does not know it
  * answers few questions about it.
  */
object KanaPlacement {
  private val failedRowsToStop = 2
  private val rowsPerBlock = 3

  /** The question to ask next, none once the test is over. `seed` varies which
    * kana of a row is written from one test to the next.
    */
  def next(answers: List[Answered], seed: Long): Option[PlacementQuestion] =
    Script.values.iterator.map(walk(_, answers, seed)._1).collectFirst {
      case Some(question) => question
    }

  def results(answers: List[Answered], seed: Long): List[ScriptResult] =
    Script.values.toList.map(walk(_, answers, seed)._2)

  /** The row a kana is in. */
  def rowOf(kana: Kana): List[Kana] =
    Kana.rows(kana.script).find(_.contains(kana)).getOrElse(List(kana))

  /** The kana of a row read right, in the chart's order. */
  def readRight(row: List[Kana], answers: List[Answered]): List[Kana] =
    row.filter(kana =>
      answer(answers, AnswerKind.Reading, kana).exists(_.correct)
    )

  private def answer(answers: List[Answered], kind: AnswerKind, kana: Kana) =
    answers.find(answered =>
      answered.kind == kind && answered.subject == kana.character
    )

  // Goes through a script's rows as far as its answers allow: the question
  // that comes next in it, if any, and what it showed so far.
  private def walk(
      script: Script,
      answers: List[Answered],
      seed: Long
  ): (Option[PlacementQuestion], ScriptResult) = {
    val rows = Kana.rows(script)
    val skipped = answers.exists(answered =>
      answered.kind == AnswerKind.Skip && answered.subject == script.toString
    )

    // Rows are asked by blocks, their kana in a random order fixed for the
    // test, so that the chart's order does not give the readings away.
    val blocks = rows.zipWithIndex.grouped(rowsPerBlock).toList

    // the next question, and the first row not mastered
    @tailrec
    def from(
        block: Int,
        failedRows: Int,
        startAt: Option[String]
    ): (Option[PlacementQuestion], Option[String]) =
      if (block >= blocks.size || failedRows >= failedRowsToStop) {
        (None, startAt)
      } else {
        val shuffled = Random((seed * 31 + script.ordinal) * 31 + block)
        val blockRows = shuffled.shuffle(blocks(block))
        val unread = shuffled
          .shuffle(blockRows.flatMap(_._1))
          .find(kana => answer(answers, AnswerKind.Reading, kana).isEmpty)
        // each row passed has one of its kana read right written: which one
        // changes from one test to the next
        def toWrite = blockRows.collectFirst(Function.unlift { (row, index) =>
          val read = readRight(row, answers)
          val written =
            row.exists(answer(answers, AnswerKind.Writing, _).isDefined)
          Option.when(read.size * 2 >= row.size && !written)(
            read(((seed + index) % read.size).toInt)
          )
        })
        unread.map(kana => PlacementQuestion.Reading(kana.character)) orElse
          toWrite.map(kana => PlacementQuestion.Writing(kana.character)) match {
          case Some(question) => (Some(question), startAt)
          case None           =>
            val (failed, start) =
              blocks(block).foldLeft((failedRows, startAt)) {
                case ((failed, start), (row, _)) =>
                  val read = readRight(row, answers)
                  val passed = read.size * 2 >= row.size
                  val mastered = read.size == row.size &&
                    row
                      .flatMap(answer(answers, AnswerKind.Writing, _))
                      .exists(_.correct)
                  (
                    if (passed) 0 else failed + 1,
                    start.orElse(Option.when(!mastered)(row.head.character))
                  )
              }
            from(block + 1, failed, start)
        }
      }

    val (question, startAt) =
      if (skipped) (None, rows.headOption.map(_.head.character))
      else from(0, 0, None)
    val asked = answers.filter(answered =>
      Kana.find(answered.subject).exists(_.script == script)
    )
    def counted(kind: AnswerKind) = asked.filter(_.kind == kind)
    question -> ScriptResult(
      script,
      skipped,
      read = counted(AnswerKind.Reading).count(_.correct),
      readAsked = counted(AnswerKind.Reading).size,
      written = counted(AnswerKind.Writing).count(_.correct),
      writtenAsked = counted(AnswerKind.Writing).size,
      startAt = startAt
    )
  }
}
