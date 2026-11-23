package hanamaru.backend.storage

import java.nio.file.Path

import utest.*

object PlacesTests extends TestSuite {
  private val home = Path.of("/home/learner")

  val tests: Tests = Tests {
    test("the XDG defaults, under the home directory") {
      val places = Places.fromEnvironment(Map.empty, home)
      assert(places.data == home.resolve(".local/share/hanamaru"))
      assert(places.cache == home.resolve(".cache/hanamaru"))
      assert(places.config == home.resolve(".config/hanamaru"))
      assert(
        places.settings == home.resolve(".config/hanamaru/hanamaru.properties")
      )
    }
    test("the XDG directories, when they are set") {
      val places = Places.fromEnvironment(
        Map("XDG_DATA_HOME" -> "/data", "XDG_CACHE_HOME" -> ""),
        home
      )
      assert(places.data == Path.of("/data/hanamaru"))
      assert(places.cache == home.resolve(".cache/hanamaru"))
    }
    test("Hanamaru's own variables, over the XDG ones") {
      val places = Places.fromEnvironment(
        Map(
          "HANAMARU_DATA" -> "/elsewhere/data",
          "XDG_DATA_HOME" -> "/data",
          "HANAMARU_CACHE" -> "/elsewhere/cache"
        ),
        home
      )
      assert(places.data == Path.of("/elsewhere/data"))
      assert(places.cache == Path.of("/elsewhere/cache"))
    }
  }
}
