package hanamaru.backend.models

import hanamaru.shared.ModelState

import java.net.http.HttpClient
import java.nio.file.{Files, Path}

import utest.*

object LlamaServerTests extends TestSuite {

  // a Hugging Face cache holding one file of one repository
  private def cacheWith(repository: String, name: String): Path = {
    val root = Files.createTempDirectory("hanamaru-hf")
    val folder = root.resolve("models--" + repository.replace("/", "--"))
    Files.createDirectories(folder.resolve("refs"))
    Files.writeString(folder.resolve("refs/main"), "abc123\n")
    val snapshot = Files.createDirectories(folder.resolve("snapshots/abc123"))
    Files.writeString(snapshot.resolve(name), "weights")
    root
  }

  private def server(settings: Map[String, String], cache: Path) =
    LlamaServer(
      LocalSettings(settings),
      Path.of("/settings/hanamaru.properties"),
      HuggingFaceCache(cache),
      Files.createTempDirectory("hanamaru-logs"),
      HttpClient.newHttpClient()
    )

  val tests: Tests = Tests {
    test("the cache: a repository's file, from the snapshot `main` names") {
      val root = cacheWith("someone/model-GGUF", "model.gguf")
      val cache = HuggingFaceCache(root)
      assert(
        cache.file("someone/model-GGUF", "model.gguf").exists(Files.exists(_))
      )
      assert(cache.file("someone/model-GGUF", "other.gguf").isEmpty)
      assert(cache.file("nobody/model", "model.gguf").isEmpty)
    }
    test("the cache's place, from the environment") {
      val home = Path.of("/home/learner")
      assert(
        HuggingFaceCache.rootOf(Map.empty, home) == home.resolve(
          ".cache/huggingface/hub"
        )
      )
      assert(
        HuggingFaceCache.rootOf(Map("HF_HOME" -> "/hf"), home) == Path.of(
          "/hf/hub"
        )
      )
      assert(
        HuggingFaceCache.rootOf(
          Map("HF_HOME" -> "/hf", "HF_HUB_CACHE" -> "/hub"),
          home
        ) ==
          Path.of("/hub")
      )
    }
    test("local settings: a properties file, none when it is missing") {
      val file = Files.createTempFile("hanamaru", ".properties")
      Files.writeString(
        file,
        "# this machine\nllama.server = /opt/llama/llama-server\nmodel.context=\n"
      )
      val settings = LocalSettings.read(file)
      assert(
        settings
          .get(LocalSettings.llamaServer)
          .contains("/opt/llama/llama-server")
      )
      assert(settings.get(LocalSettings.modelContext).isEmpty)
      assert(
        LocalSettings
          .read(file.resolveSibling("missing.properties"))
          .values
          .isEmpty
      )
    }
    test("no server set: the tutor is not set up, and says where to set it") {
      val tutor = server(Map.empty, cacheWith("a/b", "c.gguf"))
      tutor.run()
      assert(
        tutor.state == ModelState.NotConfigured(
          "llama.server",
          "/settings/hanamaru.properties"
        )
      )
    }
    test("the model not in the cache") {
      val executable = Files.createTempFile("llama-server", "")
      executable.toFile.setExecutable(true)
      val tutor = server(
        Map(
          "llama.server" -> executable.toString,
          "model.repository" -> "a/b",
          "model.file" -> "missing.gguf"
        ),
        cacheWith("a/b", "c.gguf")
      )
      tutor.run()
      assert(tutor.state == ModelState.ModelMissing("a/b/missing.gguf"))
    }
    test("a server path that is not an executable") {
      val tutor = server(
        Map("llama.server" -> "/nowhere/llama-server"),
        cacheWith("a/b", "c.gguf")
      )
      tutor.run()
      assert(tutor.state.isInstanceOf[ModelState.Failed])
    }
  }
}
