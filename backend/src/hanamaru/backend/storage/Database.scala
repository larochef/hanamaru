package hanamaru.backend.storage

import java.nio.file.Path
import java.sql.*
import java.util.concurrent.locks.ReentrantLock
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
import scala.util.Using
import scala.util.control.NonFatal

import com.typesafe.scalalogging.Logger
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult

/** A SQLite database: the learner's, or one of the reference data, its schema
  * brought up to date by Flyway when it opens ([[Database.Schema]]).
  *
  * One connection, one caller at a time: a single learner's data, whose
  * requests are short. Plain SQL with `?` parameters; a value is a String, an
  * Int, a Long, a Double, an Option of one, or null.
  */
final class Database(connection: Connection) extends AutoCloseable {
  private val lock = ReentrantLock()

  /** Runs `work` in one transaction: all of it is kept, or none. */
  def transaction[A](work: Database.Session => A): A = {
    lock.lock()
    try {
      connection.setAutoCommit(false)
      try {
        val result = work(Database.Session(connection))
        connection.commit()
        result
      } catch {
        case NonFatal(error) =>
          connection.rollback()
          throw error
      } finally {
        connection.setAutoCommit(true)
      }
    } finally {
      lock.unlock()
    }
  }

  /** Reads, in a transaction of its own. */
  def read[A](work: Database.Session => A): A = transaction(work)

  override def close(): Unit = connection.close()
}

object Database {
  private val logger = Logger("hanamaru.backend.storage")

  /** A database's schema: its Flyway migrations, in the resources at
    * `location`, and the name its log line gives it. A `baselined` database may
    * have been made before its migrations: found with tables but no migration
    * history, it is taken to be at its first migration.
    */
  final case class Schema(name: String, location: String, baselined: Boolean)

  /** The learner's data. */
  val learner: Schema =
    Schema("Learner data", "db/migration", baselined = false)

  // Flyway's own logs are kept to its warnings: this line says where the
  // schema stands, and what this start changed.
  private def migrated(schema: Schema, result: MigrateResult): String = {
    val version = Option(result.targetSchemaVersion)
      .orElse(Option(result.initialSchemaVersion))
      .fold("no schema")(version => s"schema version $version")
    val applied = result.migrations.asScala.toList
      .map(migration => s"V${migration.version} ${migration.description}")
    if (applied.isEmpty) {
      s"${schema.name}: $version, up to date"
    } else {
      s"${schema.name}: $version, applied ${applied.mkString(", ")}"
    }
  }

  /** Brings the schema of the database in `file` up to date. */
  def migrate(file: Path, schema: Schema): Unit = {
    val result = Flyway
      .configure()
      .dataSource(s"jdbc:sqlite:$file", null, null)
      .locations(s"classpath:${schema.location}")
      .baselineOnMigrate(schema.baselined)
      .baselineVersion("1")
      .load()
      .migrate()
    logger.info(migrated(schema, result))
  }

  /** The database in `file`, its schema brought up to date. */
  def open(file: Path, schema: Schema = learner): Database = {
    migrate(file, schema)
    connect(file)
  }

  /** A database as it is, its schema untouched. */
  def connect(file: Path): Database = {
    val connection = DriverManager.getConnection(s"jdbc:sqlite:$file")
    Using.resource(connection.createStatement()) { statement =>
      // readers do not wait for the writer, and references are kept
      statement.execute("PRAGMA journal_mode = WAL")
      statement.execute("PRAGMA foreign_keys = ON")
      statement.execute("PRAGMA busy_timeout = 5000")
    }
    Database(connection)
  }

  /** A row as read: its columns by name. */
  final class Row(results: ResultSet) {
    def string(column: String): String = results.getString(column)
    def stringOption(column: String): Option[String] =
      Option(results.getString(column))
    def int(column: String): Int = results.getInt(column)
    def long(column: String): Long = results.getLong(column)
    def double(column: String): Double = results.getDouble(column)
    def intOption(column: String): Option[Int] = {
      val value = results.getInt(column)
      Option.when(!results.wasNull())(value)
    }
    def longOption(column: String): Option[Long] = {
      val value = results.getLong(column)
      Option.when(!results.wasNull())(value)
    }
    def doubleOption(column: String): Option[Double] = {
      val value = results.getDouble(column)
      Option.when(!results.wasNull())(value)
    }
    def boolean(column: String): Boolean = results.getInt(column) != 0
  }

  final class Session(connection: Connection) {

    def query[A](sql: String, parameters: Any*)(read: Row => A): List[A] =
      Using.resource(prepared(sql, parameters)) { statement =>
        Using.resource(statement.executeQuery()) { results =>
          val rows = ListBuffer.empty[A]
          val row = Row(results)
          while (results.next()) {
            rows += read(row)
          }
          rows.toList
        }
      }

    def queryOne[A](sql: String, parameters: Any*)(read: Row => A): Option[A] =
      query(sql, parameters*)(read).headOption

    /** Runs a statement; the rows it changed. */
    def update(sql: String, parameters: Any*): Int =
      Using.resource(prepared(sql, parameters))(_.executeUpdate())

    /** Inserts a row; its id. */
    def insert(sql: String, parameters: Any*): Long =
      Using.resource(prepared(sql, parameters)) { statement =>
        statement.executeUpdate()
        Using.resource(connection.createStatement()) { last =>
          Using.resource(last.executeQuery("SELECT last_insert_rowid()")) {
            results =>
              results.next()
              results.getLong(1)
          }
        }
      }

    private def prepared(
        sql: String,
        parameters: Seq[Any]
    ): PreparedStatement = {
      val statement = connection.prepareStatement(sql)
      parameters.zipWithIndex.foreach { (value, index) =>
        bind(statement, index + 1, value)
      }
      statement
    }

    private def bind(statement: PreparedStatement, at: Int, value: Any): Unit =
      value match {
        case null           => statement.setNull(at, Types.NULL)
        case None           => statement.setNull(at, Types.NULL)
        case Some(inner)    => bind(statement, at, inner)
        case text: String   => statement.setString(at, text)
        case number: Int    => statement.setInt(at, number)
        case number: Long   => statement.setLong(at, number)
        case number: Double => statement.setDouble(at, number)
        case truth: Boolean => statement.setInt(at, if (truth) 1 else 0)
        case other          =>
          throw IllegalArgumentException(s"No SQL value for $other")
      }
  }
}
