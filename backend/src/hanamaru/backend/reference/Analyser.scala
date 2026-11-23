package hanamaru.backend.reference

import hanamaru.shared.kana.Romaji

import java.net.URI
import java.nio.file.*
import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*
import scala.util.Using

import com.worksap.nlp.sudachi.{Dictionary as SudachiDictionary, *}

/** A word of a sentence as the analyser splits it: as written, its dictionary
  * form, its reading in hiragana, and its part of speech (Sudachi's, from the
  * most general: 名詞, 動詞, 助詞…).
  */
final case class Token(
    surface: String,
    dictionaryForm: String,
    reading: String,
    partOfSpeech: List[String]
) {

  /** Particles, auxiliaries, punctuation, affixes: the grammar around words. */
  def isGrammar: Boolean =
    partOfSpeech.headOption.exists(Set("助詞", "助動詞", "補助記号", "接頭辞", "接尾辞", "空白"))
}

/** Sudachi (WorksApplications, Apache 2.0) with its core dictionary: splits
  * Japanese into words, each with its dictionary form and reading. The
  * dictionary is pinned and checked by its SHA-256.
  */
object Analyser {
  val version = "20261015"

  val uri: URI = URI.create(
    s"https://github.com/WorksApplications/SudachiDict/releases/download/v$version/sudachi-dictionary-$version-core.zip"
  )

  val sha256 =
    "5cf5cb7f1ea943a988e2fb5d285a9a8b3538861faefa7a2604c2cd18c387f503"

  def dictionaryIn(directory: Path): Path =
    directory.resolve(s"sudachi-$version-core.dic")

  /** The dictionary taken out of its downloaded archive, which is then let go.
    */
  def installed(archive: Path, target: Path): Unit = {
    val partial = Files.createTempFile(
      target.getParent,
      target.getFileName.toString,
      ".part"
    )
    try {
      Using.resource(ZipFile(archive.toFile)) { zip =>
        val entry = zip
          .entries()
          .asScala
          .find(_.getName.endsWith("system_core.dic"))
          .getOrElse(throw IllegalStateException(s"No dictionary in $archive"))
        Using.resource(zip.getInputStream(entry))(input =>
          Files.copy(input, partial, StandardCopyOption.REPLACE_EXISTING)
        )
      }
      Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE)
    } finally {
      Files.deleteIfExists(partial)
    }
  }
}

/** The analyser over its installed dictionary. */
final class Analyser(dictionaryFile: Path) extends AutoCloseable {
  private val dictionary =
    SudachiDictionary.load(
      Config.defaultConfig().systemDictionary(dictionaryFile)
    )

  /** A sentence split into words, the longest units Sudachi knows (its mode C):
    * 日本語 rather than 日本 and 語.
    */
  def tokens(sentence: String): List[Token] = synchronized {
    dictionary
      .tokenizer()
      // Japanese has no spaces between words: those the tutor may add are left out
      .tokenize(
        Tokenizer.SplitMode.C,
        sentence.filterNot(character => character == ' ' || character == '　')
      )
      .asScala
      .toList
      .map(morpheme =>
        Token(
          morpheme.surface(),
          morpheme.dictionaryForm(),
          Romaji.asHiragana(morpheme.readingForm()),
          morpheme.partOfSpeech().asScala.toList
        )
      )
  }

  override def close(): Unit = dictionary.close()
}
