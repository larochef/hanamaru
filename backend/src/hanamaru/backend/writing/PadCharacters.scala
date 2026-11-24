package hanamaru.backend.writing

import hanamaru.shared.writing.PadCharacter

import java.nio.file.*

import com.github.plokhotnyuk.jsoniter_scala.core.writeToArray

/** Every kana written in the pad, kept as it was drawn: one JSON line per kana,
  * appended to `file`.
  */
final class PadCharacters(file: Path) {

  def record(characters: List[PadCharacter]): Unit = synchronized {
    characters.foreach(character =>
      Files.write(
        file,
        writeToArray(character) :+ '\n'.toByte,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND
      )
    )
  }
}
