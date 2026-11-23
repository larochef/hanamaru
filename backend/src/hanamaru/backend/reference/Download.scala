package hanamaru.backend.reference

import java.io.IOException
import java.net.URI
import java.net.http.{HttpClient, HttpRequest}
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.*
import java.security.MessageDigest
import java.util.HexFormat
import scala.util.Using

/** Downloads a published file: it reaches `target` whole, and checked when its
  * SHA-256 is known, or not at all. The file's SHA-256 is returned.
  */
final class Download(http: HttpClient) {

  def fetched(uri: URI, sha256: Option[String], target: Path): String = {
    Files.createDirectories(target.getParent)
    val partial = Files.createTempFile(
      target.getParent,
      target.getFileName.toString,
      ".part"
    )
    try {
      val response = http.send(
        HttpRequest.newBuilder(uri).GET().build(),
        BodyHandlers.ofFile(partial)
      )
      if (response.statusCode() != 200) {
        throw IOException(s"$uri answered ${response.statusCode()}")
      }
      val actual = Download.sha256Of(partial)
      sha256.filter(_ != actual).foreach { expected =>
        throw IOException(s"$uri has SHA-256 $actual, not $expected")
      }
      Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE)
      actual
    } finally {
      Files.deleteIfExists(partial)
    }
  }
}

object Download {

  def sha256Of(file: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    Using.resource(Files.newInputStream(file)) { input =>
      val buffer = new Array[Byte](1 << 16)
      var read = input.read(buffer)
      while (read >= 0) {
        digest.update(buffer, 0, read)
        read = input.read(buffer)
      }
    }
    HexFormat.of().formatHex(digest.digest())
  }
}
