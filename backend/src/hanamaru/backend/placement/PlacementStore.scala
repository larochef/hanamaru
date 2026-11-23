package hanamaru.backend.placement

import hanamaru.backend.storage.Database

import java.time.Instant

/** A placement test taken: its id, and whether it is finished. */
final case class PlacementRun(id: Long, finished: Boolean)

/** The placement tests taken and their answers, in the learner's database. */
final class PlacementStore(database: Database) {

  /** The last test taken for a domain, finished or not. */
  def lastRun(domain: String): Option[PlacementRun] = database.read(
    _.queryOne(
      "SELECT id, finished_at FROM placement_run WHERE domain = ? ORDER BY id DESC LIMIT 1",
      domain
    )(row =>
      PlacementRun(row.long("id"), row.stringOption("finished_at").isDefined)
    )
  )

  def started(domain: String, at: Instant): PlacementRun =
    database.transaction { session =>
      PlacementRun(
        session.insert(
          "INSERT INTO placement_run (domain, started_at) VALUES (?, ?)",
          domain,
          at.toEpochMilli
        ),
        finished = false
      )
    }

  /** A test's answers, in the order they were given. */
  def answers(run: Long): List[Answered] = database.read(
    _.query(
      "SELECT question, subject, correct FROM placement_answer WHERE run_id = ? ORDER BY id",
      run
    )(row =>
      Answered(
        AnswerKind.values
          .find(_.name == row.string("question"))
          .getOrElse(throw IllegalStateException(row.string("question"))),
        row.string("subject"),
        row.boolean("correct")
      )
    )
  )

  def record(run: Long, answered: Answered, answer: String, at: Instant): Unit =
    database.transaction(
      _.insert(
        """INSERT INTO placement_answer (run_id, question, subject, correct, answer,
          |answered_at) VALUES (?, ?, ?, ?, ?, ?)""".stripMargin,
        run,
        answered.kind.name,
        answered.subject,
        answered.correct,
        answer,
        at.toEpochMilli
      )
    )

  def finish(run: Long, at: Instant): Unit = database.transaction(
    _.update(
      "UPDATE placement_run SET finished_at = ? WHERE id = ?",
      at.toEpochMilli,
      run
    )
  )
}
