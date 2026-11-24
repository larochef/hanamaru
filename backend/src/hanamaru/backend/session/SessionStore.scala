package hanamaru.backend.session

import hanamaru.backend.storage.Database
import hanamaru.shared.kana.Script
import hanamaru.shared.session.{SessionExercise, SessionStart}

import java.time.Instant

import com.github.plokhotnyuk.jsoniter_scala.core.{
  readFromString,
  writeToString
}

/** A study session as kept: what was declared at its start (the kana script it
  * works on among it, none for both), the exercise it asks now, when it
  * started, and whether it is over.
  */
final case class StudySession(
    id: Long,
    minutes: Int,
    stylus: Boolean,
    script: Option[Script],
    current: Option[SessionExercise],
    startedAt: Long,
    ended: Boolean
) {
  def endsAt: Long = startedAt + minutes * 60 * 1000L
}

/** The study sessions, in the learner's database. */
final class SessionStore(database: Database) {

  def started(format: String, start: SessionStart, at: Instant): StudySession =
    database.transaction { session =>
      val id = session.insert(
        """INSERT INTO study_session (format, budget_minutes, stylus, kana_script,
          |started_at) VALUES (?, ?, ?, ?, ?)""".stripMargin,
        format,
        start.minutes,
        start.stylus,
        start.script.map(_.toString),
        at.toEpochMilli
      )
      StudySession(
        id,
        start.minutes,
        start.stylus,
        start.script,
        None,
        at.toEpochMilli,
        ended = false
      )
    }

  def find(id: Long): Option[StudySession] = database.read(
    _.queryOne("SELECT * FROM study_session WHERE id = ?", id)(row =>
      StudySession(
        row.long("id"),
        row.int("budget_minutes"),
        row.boolean("stylus"),
        row.stringOption("kana_script").map(Script.valueOf),
        row
          .stringOption("current_exercise")
          .map(readFromString[SessionExercise](_)),
        row.long("started_at"),
        row.stringOption("ended_at").isDefined
      )
    )
  )

  /** Sets the exercise asked now; none ends the session. */
  def asking(id: Long, exercise: Option[SessionExercise], at: Instant): Unit =
    database.transaction(
      _.update(
        "UPDATE study_session SET current_exercise = ?, ended_at = ? WHERE id = ?",
        exercise.map(writeToString(_)),
        Option.when(exercise.isEmpty)(at.toEpochMilli),
        id
      )
    )
}
