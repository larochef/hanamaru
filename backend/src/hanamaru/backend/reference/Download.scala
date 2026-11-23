package hanamaru.backend.reference

import java.io.IOException
import java.net.URI
import java.net.http.{HttpClient, HttpRequest}
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.*
import java.security.MessageDigest
import java.util.HexFormat
import scala.util.Using

/** Downloads a published file whose SHA-256 is known: it reaches `target` whole
  * and checked, or not at all.
  */
final class Download(http: HttpClient) {

  def fetched(uri: URI, sha256: String, target: Path): Unit = {
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
      if (actual != sha256) {
        throw IOException(s"$uri has SHA-256 $actual, not $sha256")
      }
      Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE)
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
