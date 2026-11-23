package hanamaru.backend.reference

import hanamaru.backend.storage.Database
import hanamaru.shared.dictionary.Meaning

import java.io.ByteArrayInputStream
import java.nio.file.Files

import utest.*

object JmdictTests extends TestSuite {

  val tests: Tests = Tests {
    test(
      "entries: forms, codes, senses in the languages kept, frequency rank"
    ) {
      val entries = scala.collection.mutable.ListBuffer.empty[Jmdict.Entry]
      Jmdict.entries(ByteArrayInputStream(SampleJmdict.gzipped))(entries += _)
      val List(cat, pretty, rare) = entries.toList: @unchecked
      assert(cat.id == 2000002, cat.rank.contains(20), cat.common)
      assert(cat.senses.map(_.language) == List("eng", "fre"))
      assert(cat.senses.head.partsOfSpeech == List("n"))
      assert(pretty.rank.contains(2))
      assert(pretty.forms.map(_.text) == List("綺麗", "奇麗", "きれい"))
      assert(pretty.forms(1).info == List("rK"))
      assert(pretty.senses.head.misc == List("uk"))
      assert(rare.rank.isEmpty, !rare.common)
      assert(
        Jmdict
          .createdIn(ByteArrayInputStream(SampleJmdict.gzipped))
          .contains("2026-01-02")
      )
    }
    test(
      "a JLPT list: its entries in order, quoted fields, words with no entry left out"
    ) {
      val list = Files.createTempFile("jlpt", ".csv")
      Files.writeString(
        list,
        "jmdict_seq,kana,kanji,waller_definition\n" +
          "1000360,あっさり,,\"easily, readily\"\n" +
          ",い,依,depending on\n" +
          "1198180,あう,会う,to meet\n"
      )
      assert(JlptLists.entries(list) == List(1000360L, 1198180L))
    }
    test(
      "the core vocabulary: by JLPT level, the most frequent first within one"
    ) {
      val directory = Files.createTempDirectory("hanamaru-jmdict")
      val source = directory.resolve("JMdict.gz")
      Files.write(source, SampleJmdict.gzipped)
      val database = directory.resolve("dictionary.db")
      // 猫 in N5; きれい (ranked) and まれ (not) in N4; 猫 again in N3
      val core = List(
        5 -> List(2000002L),
        4 -> List(2000003L, 2000001L),
        3 -> List(2000002L)
      )
      assert(
        Jmdict.imported(
          source,
          core,
          database,
          Map("created" -> "2026-01-02")
        ) == 3
      )
      val dictionary = Dictionary(Database.connect(database))
      assert(dictionary.version.get("created").contains("2026-01-02"))
      val words = dictionary.coreWords(0, 10)
      assert(words.map(_.id) == List(2000002, 2000001, 2000003))
      assert(words.map(_.level) == List(5, 4, 4))
      val pretty = words(1)
      assert(
        pretty.writings == List("綺麗"),
        pretty.readings == List("きれい"),
        pretty.usuallyKana
      )
      assert(words.head.meanings.contains(Meaning("fre", Nil, List("chat"))))
      assert(dictionary.coreWords(1, 10).map(_.id) == List(2000001, 2000003))
    }
  }
}
