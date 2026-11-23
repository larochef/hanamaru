package hanamaru.backend.reference

import hanamaru.shared.writing.*

import java.io.{IOException, InputStream}
import java.net.URI
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import scala.util.Using

import org.w3c.dom.{Element, NodeList}

/** KanjiVG (https://kanjivg.tagaini.net, CC BY-SA 3.0): the strokes of every
  * kana and kanji, in order, one SVG file per character in its release's
  * archive. The release is pinned, and checked by its SHA-256.
  */
object KanjiVg {
  val version = "20260714"

  val uri: URI = URI.create(
    s"https://github.com/KanjiVG/kanjivg/releases/download/r$version/kanjivg-$version-main.zip"
  )

  val sha256 =
    "b5df6cd2bc249dd49b8041eb99e38ba9f8dc6b9ea57962a80084b4a315d1a7fc"

  def archiveIn(directory: Path): Path =
    directory.resolve(s"kanjivg-$version-main.zip")

  /** The archive's file for a single character, named by its code point. */
  def entryOf(character: String): Option[String] =
    Option.when(
      !character.isEmpty && character.codePointCount(0, character.length) == 1
    )(f"kanji/${character.codePointAt(0)}%05x.svg")

  private val strokeId = """.*-s(\d+)$""".r
  private val labelTransform = """matrix\(1 0 0 1 (\S+) (\S+)\)""".r

  /** The strokes of one character's file, in the order they are written. */
  def strokes(svg: InputStream): List[Stroke] = {
    val document = parser().parse(svg)
    val groups = elements(document.getElementsByTagName("g"))
    def group(prefix: String): Element =
      groups
        .find(_.getAttribute("id").startsWith(prefix))
        .getOrElse(throw IOException(s"No $prefix group"))
    val paths = elements(
      group("kvg:StrokePaths_").getElementsByTagName("path")
    ).map { path =>
      path.getAttribute("id") match {
        case strokeId(number) => number.toInt -> path.getAttribute("d")
        case other => throw IOException(s"Stroke without a number: $other")
      }
    }
    val labels = elements(
      group("kvg:StrokeNumbers_").getElementsByTagName("text")
    ).map { text =>
      text.getAttribute("transform") match {
        case labelTransform(x, y) =>
          text.getTextContent.trim.toInt -> Point(x.toDouble, y.toDouble)
        case other => throw IOException(s"Unexpected number placement: $other")
      }
    }.toMap
    paths.sortBy(_._1).map { (number, path) =>
      Stroke(
        path,
        labels
          .getOrElse(number, throw IOException(s"Stroke $number has no number"))
      )
    }
  }

  // The files declare a DTD on w3.org: it is never fetched, and no external
  // entity is read.
  private def parser() = {
    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature(
      "http://apache.org/xml/features/nonvalidating/load-external-dtd",
      false
    )
    factory.setFeature(
      "http://xml.org/sax/features/external-general-entities",
      false
    )
    factory.setFeature(
      "http://xml.org/sax/features/external-parameter-entities",
      false
    )
    factory.setExpandEntityReferences(false)
    factory.newDocumentBuilder()
  }

  private def elements(nodes: NodeList): List[Element] =
    List.tabulate(nodes.getLength)(nodes.item).collect {
      case element: Element =>
        element
    }
}

/** The downloaded release, read character by character. */
final class KanjiVgArchive(file: Path) extends AutoCloseable {
  private val zip = ZipFile(file.toFile)

  def strokes(character: String): Option[CharacterStrokes] =
    KanjiVg
      .entryOf(character)
      .flatMap(name => Option(zip.getEntry(name)))
      .map(entry =>
        CharacterStrokes(
          character,
          Using.resource(zip.getInputStream(entry))(KanjiVg.strokes)
        )
      )

  override def close(): Unit = zip.close()
}
