package hanamaru.backend.reference

import hanamaru.backend.storage.Database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.GZIPOutputStream
import scala.util.Using

/** A small JMdict, made up, for the tests that need a dictionary. */
object SampleJmdict {

  // Laid out as JMdict is, with invented entries: a DTD whose entities are
  // codes, senses in several languages, frequency marks, rare forms.
  val xml: String =
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

  def gzipped: Array[Byte] = {
    val bytes = java.io.ByteArrayOutputStream()
    Using.resource(GZIPOutputStream(bytes))(
      _.write(xml.getBytes(StandardCharsets.UTF_8))
    )
    bytes.toByteArray
  }

  /** The sample imported into a dictionary in `directory`, with a core list: 猫
    * in N5, まれ and きれい in N4.
    */
  def dictionaryIn(directory: Path): Dictionary = {
    val source = directory.resolve("JMdict.gz")
    Files.write(source, gzipped)
    val database = directory.resolve("dictionary.db")
    Jmdict.imported(
      source,
      List(5 -> List(2000002L), 4 -> List(2000003L, 2000001L)),
      database,
      Map("created" -> "2026-01-02")
    )
    Dictionary(Database.connect(database))
  }
}
