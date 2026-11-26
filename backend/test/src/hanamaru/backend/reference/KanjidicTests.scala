package hanamaru.backend.reference

import hanamaru.backend.storage.Database

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

import utest.*

object KanjidicTests extends TestSuite {

  // laid out as KANJIDIC2 is: a header, characters with readings in several
  // languages, meanings in several, and a rare character with neither grade
  // nor frequency
  private val xml =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<!DOCTYPE kanjidic2 [
      |<!ELEMENT kanjidic2 (header,character*)>
      |<!ELEMENT literal (#PCDATA)>
      |]>
      |<kanjidic2>
      |<header><file_version>4</file_version><database_version>2026-325</database_version>
      |<date_of_creation>2026-11-21</date_of_creation></header>
      |<character><literal>日</literal>
      |<misc><grade>1</grade><stroke_count>4</stroke_count><stroke_count>3</stroke_count><freq>1</freq><jlpt>4</jlpt></misc>
      |<reading_meaning><rmgroup>
      |<reading r_type="pinyin">ri4</reading><reading r_type="ja_on">ニチ</reading><reading r_type="ja_on">ジツ</reading>
      |<reading r_type="ja_kun">ひ</reading><reading r_type="ja_kun">-か</reading>
      |<meaning>day</meaning><meaning>sun</meaning><meaning m_lang="fr">jour</meaning><meaning m_lang="fr">soleil</meaning>
      |<meaning m_lang="es">día</meaning>
      |</rmgroup><nanori>あき</nanori></reading_meaning></character>
      |<character><literal>月</literal>
      |<misc><grade>1</grade><stroke_count>4</stroke_count><freq>23</freq></misc>
      |<reading_meaning><rmgroup><reading r_type="ja_on">ゲツ</reading><reading r_type="ja_kun">つき</reading>
      |<meaning>month</meaning><meaning>moon</meaning></rmgroup></reading_meaning></character>
      |<character><literal>丂</literal><misc><stroke_count>2</stroke_count></misc></character>
      |</kanjidic2>""".stripMargin

  private def gzipped: Array[Byte] = {
    val bytes = ByteArrayOutputStream()
    val zip = GZIPOutputStream(bytes)
    zip.write(xml.getBytes(StandardCharsets.UTF_8))
    zip.close()
    bytes.toByteArray
  }

  val tests: Tests = Tests {
    test(
      "imported: graded or frequent kanji, with on and kun readings, French meanings first"
    ) {
      val directory = Files.createTempDirectory("hanamaru-kanjidic")
      val source = directory.resolve("kanjidic2.xml.gz")
      Files.write(source, gzipped)
      val database = directory.resolve("kanji.db")
      val count = Kanjidic.imported(
        source,
        database,
        Map("schema" -> Kanjidic.importVersion)
      )
      assert(count == 2)
      val book = KanjiBook(Database.connect(database))
      val first = book.ofGrade(1)
      assert(first.map(_.literal) == List("日", "月"))
      val day = first.head
      assert(
        day.strokes == 4,
        day.on == List("ニチ", "ジツ"),
        day.kun == List("ひ", "-か")
      )
      assert(day.meanings == List("jour", "soleil"), !day.inEnglish)
      // no French meaning: the English ones, said so
      assert(first(1).meanings == List("month", "moon"), first(1).inEnglish)
      assert(book.kanji("丂").isEmpty)
      assert(book.version.get("schema").contains(Kanjidic.importVersion))
    }
    test("the file's date, from its header") {
      assert(
        Kanjidic
          .createdIn(java.io.ByteArrayInputStream(gzipped))
          .contains("2026-11-21")
      )
    }
  }
}
