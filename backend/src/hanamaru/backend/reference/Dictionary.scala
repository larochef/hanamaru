package hanamaru.backend.reference

import hanamaru.backend.storage.Database
import hanamaru.shared.dictionary.{DictionaryWord, Meaning}

import com.github.plokhotnyuk.jsoniter_scala.core.readFromString

/** The dictionary, as imported from JMdict, read word by word. */
final class Dictionary(database: Database) {
  import Jmdict.given

  // forms only searched for, outdated, irregular or rare: not shown
  private val hiddenForms =
    Set("sK", "sk", "oK", "ok", "iK", "ik", "io", "rK", "rk")

  /** What the dictionary was imported from: its date and SHA-256. */
  def version: Map[String, String] = database.read(
    _.query("SELECT key, value FROM dictionary_info")(row =>
      row.string("key") -> row.string("value")
    ).toMap
  )

  /** The core vocabulary, in the order it is learned: by JLPT level, N5 first,
    * and within a level the words JMdict ranks most frequent first.
    */
  def coreWords(from: Int, count: Int): List[DictionaryWord] = database.read {
    session =>
      val ids = session.query(
        """SELECT core_word.word_id FROM core_word
          |JOIN word ON word.id = core_word.word_id
          |ORDER BY core_word.level DESC, word.rank IS NULL, word.rank, core_word.position
          |LIMIT ? OFFSET ?""".stripMargin,
        count,
        from
      )(_.long("word_id"))
      described(session, ids)
  }

  /** The words of the core vocabulary that are not in `met`, in the order they
    * are learned: the next ones to learn.
    */
  def nextWords(met: Set[Long], count: Int): List[DictionaryWord] =
    database.read { session =>
      val ids = session
        .query(
          """SELECT core_word.word_id FROM core_word
            |JOIN word ON word.id = core_word.word_id
            |ORDER BY core_word.level DESC, word.rank IS NULL, word.rank, core_word.position""".stripMargin
        )(_.long("word_id"))
        .iterator
        .filterNot(met)
        .take(count)
        .toList
      described(session, ids)
    }

  /** Words by their JMdict numbers, in that order; a number not in the
    * dictionary is left out.
    */
  def words(ids: List[Long]): List[DictionaryWord] =
    database.read(described(_, ids))

  /** The words one of whose forms or readings is `text`. */
  def withForm(text: String): List[DictionaryWord] = database.read { session =>
    described(
      session,
      session.query("SELECT DISTINCT word_id FROM form WHERE text = ?", text)(
        _.long("word_id")
      )
    )
  }

  // each word with its shown forms, its senses, and the JLPT level of its
  // list (0 when it is in none)
  private def described(
      session: Database.Session,
      ids: List[Long]
  ): List[DictionaryWord] =
    if (ids.isEmpty) {
      Nil
    } else {
      val inList = ids.mkString(",")
      val levels = session
        .query(
          s"SELECT word_id, level FROM core_word WHERE word_id IN ($inList)"
        )(row => row.long("word_id") -> row.int("level"))
        .toMap
      val known = session
        .query(s"SELECT id FROM word WHERE id IN ($inList)")(_.long("id"))
        .toSet
      val forms = session
        .query(
          s"SELECT word_id, kana, text, info FROM form WHERE word_id IN ($inList) ORDER BY word_id, position"
        )(row =>
          (
            row.long("word_id"),
            row.boolean("kana"),
            row.string("text"),
            readFromString[List[String]](row.string("info"))
          )
        )
        .filter((_, _, _, info) => !info.exists(hiddenForms))
        .groupBy(_._1)
      val senses = session
        .query(
          s"""SELECT word_id, language, parts_of_speech, misc, glosses FROM sense
             |WHERE word_id IN ($inList) ORDER BY word_id, position""".stripMargin
        )(row =>
          row.long("word_id") -> (
            Meaning(
              row.string("language"),
              readFromString[List[String]](row.string("parts_of_speech")),
              readFromString[List[String]](row.string("glosses"))
            ),
            readFromString[List[String]](row.string("misc"))
          )
        )
        .groupMap(_._1)(_._2)
      ids.filter(known).map { id =>
        val wordForms = forms.getOrElse(id, Nil)
        val wordSenses = senses.getOrElse(id, Nil)
        DictionaryWord(
          id,
          levels.getOrElse(id, 0),
          writings = wordForms.collect { case (_, false, text, _) => text },
          readings = wordForms.collect { case (_, true, text, _) => text },
          usuallyKana = wordSenses.headOption.exists(_._2.contains("uk")),
          meanings = wordSenses.map(_._1)
        )
      }
    }
}
