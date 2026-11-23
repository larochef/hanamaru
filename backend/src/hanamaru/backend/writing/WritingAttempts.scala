package hanamaru.backend.writing

import hanamaru.shared.writing.WritingAttempt

import java.nio.file.*

import com.github.plokhotnyuk.jsoniter_scala.core.writeToArray

/** Every character written, kept as it was drawn: one JSON line per attempt,
  * appended to `file`.
  */
final class WritingAttempts(file: Path) {

  def record(attempt: WritingAttempt): Unit = synchronized {
    Files.write(
      file,
      writeToArray(attempt) :+ '\n'.toByte,
      StandardOpenOption.CREATE,
      StandardOpenOption.APPEND
    )
  }
}
