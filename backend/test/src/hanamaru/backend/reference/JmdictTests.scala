package hanamaru.backend.reference

import hanamaru.backend.storage.Database
import hanamaru.shared.dictionary.Meaning

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import scala.util.Using

import utest.*

object JmdictTests extends TestSuite {

  // Laid out as JMdict is, with invented entries: a DTD whose entities are
  // codes, senses in several languages, frequency marks, rare forms.
  private val xml =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<!DOCTYPE JMdict [
      |<!ELEMENT JMdict (entry*)>
      |<!ENTITY n "noun (common) (futsuumeishi)">
      |<!ENTITY adj-na "adjectival nouns or quasi-adjectives (keiyodoshi)">
      |<!ENTITY uk "word usually written using kana alone">
      |<!ENTITY rK "rarely used kanji form">
      |]>
      |<JMdict>
      |<!-- JMdict created: 2026-01-02 -->
      |<entry>
      |<ent_seq>2000002</ent_seq>
      |<k_ele><keb>猫</keb><ke_pri>news1</ke_pri><ke_pri>nf20</ke_pri></k_ele>
      |<r_ele><reb>ねこ</reb><re_pri>nf20</re_pri></r_ele>
      |<sense><pos>&n;</pos><gloss>cat</gloss></sense>
      |<sense><gloss xml:lang="fre">chat</gloss></sense>
      |<sense><gloss xml:lang="ger">Katze</gloss></sense>
      |</entry>
      |<entry>
      |<ent_seq>2000001</ent_seq>
      |<k_ele><keb>綺麗</keb><ke_pri>ichi1</ke_pri><ke_pri>nf02</ke_pri></k_ele>
      |<k_ele><keb>奇麗</keb><ke_inf>&rK;</ke_inf></k_ele>
      |<r_ele><reb>きれい</reb><re_pri>nf02</re_pri></r_ele>
      |<sense><pos>&adj-na;</pos><misc>&uk;</misc><gloss>pretty</gloss><gloss>clean</gloss></sense>
      |</entry>
      |<entry>
      |<ent_seq>2000003</ent_seq>
      |<r_ele><reb>まれ</reb></r_ele>
      |<sense><pos>&n;</pos><gloss>rare word</gloss></sense>
      |</entry>
      |</JMdict>
      |""".stripMargin

  private def gzipped: Array[Byte] = {
    val bytes = java.io.ByteArrayOutputStream()
    Using.resource(GZIPOutputStream(bytes))(
      _.write(xml.getBytes(StandardCharsets.UTF_8))
    )
    bytes.toByteArray
  }

  val tests: Tests = Tests {
    test(
      "entries: forms, codes, senses in the languages kept, frequency rank"
    ) {
      val entries = scala.collection.mutable.ListBuffer.empty[Jmdict.Entry]
      Jmdict.entries(ByteArrayInputStream(gzipped))(entries += _)
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
        Jmdict.createdIn(ByteArrayInputStream(gzipped)).contains("2026-01-02")
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
      Files.write(source, gzipped)
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
