package hanamaru.backend.models

import java.net.http.{HttpClient, HttpRequest}
import java.net.http.HttpResponse.BodyHandlers
import java.nio.charset.StandardCharsets
import java.time.Duration
import scala.util.control.NonFatal

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

/** Asks the tutor's model, through `llama-server`'s OpenAI-compatible API, for
  * an answer shaped by a JSON schema: it can only answer in that shape, which
  * the backend reads without guessing.
  */
final class LlamaClient(server: LlamaServer, http: HttpClient) {
  import LlamaClient.*

  private val timeout = Duration.ofSeconds(30)

  /** The model's answer, read as an `A`; why there is none otherwise. */
  def structured[A: JsonValueCodec](
      system: String,
      user: String,
      schema: String,
      maxTokens: Int = 512
  ): Either[String, A] =
    server.uri.toRight("The tutor is not ready").flatMap { uri =>
      val request = ChatRequest(
        server.modelName,
        List(ChatMessage("system", system), ChatMessage("user", user)),
        ResponseFormat(
          "json_schema",
          JsonSchema("answer", RawJson(schema.getBytes(StandardCharsets.UTF_8)))
        ),
        temperature = 0,
        max_tokens = maxTokens
      )
      try {
        val response = http.send(
          HttpRequest
            .newBuilder(uri.resolve("/v1/chat/completions"))
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(writeToArray(request)))
            .build(),
          BodyHandlers.ofByteArray()
        )
        if (response.statusCode() != 200) {
          Left(s"The tutor answered ${response.statusCode()}")
        } else {
          readFromArray[ChatResponse](response.body()).choices.headOption
            .toRight("The tutor gave no answer")
            .map(choice => readFromString[A](choice.message.content))
        }
      } catch {
        case NonFatal(error) =>
          Left(Option(error.getMessage).getOrElse(error.toString))
      }
    }
}

object LlamaClient {

  /** JSON written as it is, as a schema is. */
  final case class RawJson(bytes: Array[Byte])

  given JsonValueCodec[RawJson] = new JsonValueCodec[RawJson] {
    override def decodeValue(in: JsonReader, default: RawJson): RawJson =
      RawJson(in.readRawValAsBytes())
    override def encodeValue(value: RawJson, out: JsonWriter): Unit =
      out.writeRawVal(value.bytes)
    override def nullValue: RawJson = null
  }

  final case class ChatMessage(role: String, content: String)
  final case class JsonSchema(name: String, schema: RawJson)
  final case class ResponseFormat(`type`: String, json_schema: JsonSchema)
  final case class ChatRequest(
      model: String,
      messages: List[ChatMessage],
      response_format: ResponseFormat,
      temperature: Double,
      max_tokens: Int
  )
  final case class Choice(message: ChatMessage)
  final case class ChatResponse(choices: List[Choice])

  given JsonValueCodec[ChatRequest] = JsonCodecMaker.make
  given JsonValueCodec[ChatResponse] = JsonCodecMaker.make
}
