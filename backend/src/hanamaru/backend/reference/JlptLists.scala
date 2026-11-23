package hanamaru.backend.reference

import java.net.URI
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The JLPT vocabulary lists, N5 (the first words) to N1: Jonathan Waller's
  * lists (tanos.co.uk, CC BY), each word tied to its JMdict entry by
  * yomitan-jlpt-vocab (github.com/stephenmk/yomitan-jlpt-vocab, CC BY-SA 4.0).
  * There are no official lists since 2010; these are the learners' reference.
  * Pinned to one commit, each file checked by its SHA-256.
  */
object JlptLists {
  val commit = "b062d4e38c4bdd0950ae1d4ec55f04b176182e03"

  /** Each level, from N5 to N1, with its file's SHA-256. */
  val levels: List[(Int, String)] = List(
    5 -> "07dc6f197b51cc076c65c3c9f23218d10c2b67f76545f5c1c4d9e3750495533a",
    4 -> "a14dcc7fdc02259b22331a486c6ff66df74c16472c8aeb5a0ebe7e5fa8ee8eb4",
    3 -> "bd4d68c59cfee861351e1bdf9d99818e434392201eabbed4d3657225fe53a3ac",
    2 -> "42a4413326e857d701dc9659b2711637ab612304a339fe0d09f4f0f042d3a214",
    1 -> "7a58f0584e9ec2b0299ccb9b109f3bd03e08b90d129a714307c0a1cb72f07b32"
  )

  def uri(level: Int): URI = URI.create(
    s"https://raw.githubusercontent.com/stephenmk/yomitan-jlpt-vocab/$commit/original_data/n$level.csv"
  )

  def fileIn(directory: Path, level: Int): Path =
    directory.resolve(s"jlpt-n$level.csv")

  /** The JMdict entries of a list, in its order; a word with no entry is left
    * out.
    */
  def entries(file: Path): List[Long] =
    Files
      .readAllLines(file)
      .asScala
      .toList
      .drop(1)
      .flatMap(fields(_).headOption)
      .flatMap(_.trim.toLongOption)

  // a line of comma-separated fields, a field possibly in double quotes
  private def fields(line: String): List[String] = {
    val found = List.newBuilder[String]
    val field = StringBuilder()
    var quoted = false
    var index = 0
    while (index < line.length) {
      val character = line(index)
      if (quoted && character == '"' && line.lift(index + 1).contains('"')) {
        field += '"'
        index += 1
      } else if (character == '"') {
        quoted = !quoted
      } else if (character == ',' && !quoted) {
        found += field.result()
        field.clear()
      } else {
        field += character
      }
      index += 1
    }
    found += field.result()
    found.result()
  }
}
