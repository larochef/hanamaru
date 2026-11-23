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
      val ranked = session.query(
        """SELECT core_word.word_id, core_word.level FROM core_word
          |JOIN word ON word.id = core_word.word_id
          |ORDER BY core_word.level DESC, word.rank IS NULL, word.rank, core_word.position
          |LIMIT ? OFFSET ?""".stripMargin,
        count,
        from
      )(row => row.long("word_id") -> row.int("level"))
      val ids = ranked.map(_._1)
      if (ids.isEmpty) {
        Nil
      } else {
        val inList = ids.mkString(",")
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
        ranked.map { (id, level) =>
          val wordForms = forms.getOrElse(id, Nil)
          val wordSenses = senses.getOrElse(id, Nil)
          DictionaryWord(
            id,
            level,
            writings = wordForms.collect { case (_, false, text, _) => text },
            readings = wordForms.collect { case (_, true, text, _) => text },
            usuallyKana = wordSenses.headOption.exists(_._2.contains("uk")),
            meanings = wordSenses.map(_._1)
          )
        }
      }
  }
}
