package io.tolgee.socketio

import com.corundumstudio.socketio.SocketIOServer
import io.socket.client.IO
import io.socket.client.Socket
import io.tolgee.AbstractSpringTest
import io.tolgee.assertions.Assertions
import io.tolgee.development.testDataBuilder.data.BaseTestData
import io.tolgee.fixtures.WaitNotSatisfiedException
import io.tolgee.fixtures.isValidId
import io.tolgee.fixtures.waitFor
import io.tolgee.model.Project
import io.tolgee.model.enums.ApiScope
import io.tolgee.model.key.Key
import io.tolgee.model.translation.Translation
import net.javacrumbs.jsonunit.assertj.assertThatJson
import org.json.JSONObject
import org.springframework.beans.factory.annotation.Autowired
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test
import java.net.URI

abstract class AbstractSocketIoTest : AbstractSpringTest() {
  protected lateinit var sockets: List<Socket>
  lateinit var project: Project
  lateinit var testData: BaseTestData
  lateinit var translation: Translation
  lateinit var key: Key

  var socketPorts = mutableListOf("19090")

  @Autowired
  lateinit var socketIOServer: SocketIOServer

  /**
   * Asserts that event with provided name was triggered by runnable provided in "dispatch" function
   */
  fun assertNotified(
    eventName: String,
    dispatchCallback: () -> Unit,
    assertCallback: ((value: JSONObject) -> Unit)? = null
  ) {
    commitTransaction()
    val notified = sockets.associateWith { false }.toMutableMap()
    sockets.forEach { socket ->
      socket.on(eventName) { data ->
        notified[socket] = true
        assertCallback?.let { it(data[0] as JSONObject) }
      }
    }

    dispatchCallback()

    sockets.forEach { socket ->
      try {
        waitFor(3000) {
          notified[socket] ?: false
        }
      } catch (e: WaitNotSatisfiedException) {
        e.printStackTrace()
      }
      Assertions.assertThat(notified[socket]).isTrue
        .withFailMessage("Socket IO client was not notified")
    }
  }

  fun beforePrepareSockets() {
  }

  @BeforeClass
  fun beforeClass() {
    prepareTestData()
    prepareSockets()
  }

  @AfterClass
  fun afterClass() {
    socketIOServer.stop()
    sockets.forEach { it.disconnect() }
  }

  fun prepareSockets() {
    beforePrepareSockets()
    project = testData.projectBuilder.self
    val apiKey = apiKeyService.create(project.userOwner!!, setOf(ApiScope.TRANSLATIONS_VIEW), project).key
    sockets = socketPorts.map {
      IO.socket(URI.create("http://localhost:$it/translations?apiKey=$apiKey"))
    }
    sockets.parallelStream().forEach { socket ->
      var connected = false
      socket.on(Socket.EVENT_CONNECT) {
        connected = true
      }

      socket.on("connect_error") {
        println("connection error!")
      }

      socket.connect()

      waitFor(30000) {
        connected
      }
    }
  }

  fun prepareTestData() {
    testData = BaseTestData()
    testData.projectBuilder.apply {
      addKey {
        self {
          name = "key"
          key = this
        }
        addTranslation {
          self {
            language = testData.englishLanguage
            text = "translation"
            translation = this
            key = this@addKey.self
          }
        }
      }
    }
    testDataService.saveTestData(testData.root)
  }

  fun assertKeyModify() {
    assertNotified(
      "key_modified",
      {
        keyService.edit(key, "name edited")
      },
      {
        assertThatJson(it.toString()).apply {
          node("id").isValidId
          node("oldName").isEqualTo("key")
          node("name").isEqualTo("name edited")
        }
      }
    )
  }

  fun assertKeyDelete() {
    assertNotified(
      "key_deleted",
      {
        keyService.delete(key.id)
      },
      {
        assertThatJson(it.toString()).apply {
          node("id").isValidId
          node("name").isEqualTo("key")
        }
      }
    )
  }

  fun assertKeyCreate() {
    assertNotified(
      "key_created",
      {
        keyService.create(project, "created_key")
      },
      {
        assertThatJson(it.toString()).apply {
          node("id").isValidId
          node("name").isEqualTo("created_key")
        }
      }
    )
  }

  fun assertTranslationModify() {
    assertNotified(
      "translation_modified",
      {
        translationService.saveTranslation(translation.also { it.text = "modified text" })
      },
      {
        assertThatJson(it.toString()).apply {
          node("id").isValidId
          node("text").isEqualTo("modified text")
          node("languageTag").isEqualTo("en")
        }
      }
    )
  }

  fun assertTranslationDelete() {
    assertNotified(
      "translation_deleted",
      {
        translationService.deleteIfExists(translation.key!!, "en")
      },
      {
        assertThatJson(it.toString()).apply {
          node("id").isValidId
          node("text").isEqualTo(translation.text)
          node("languageTag").isEqualTo("en")
        }
      }
    )
  }

  @Test
  fun assertTranslationCreate() {
    assertNotified(
      "translation_created",
      {
        translationService.saveTranslation(
          Translation().apply {
            text = "created translation"
            this.key = this@AbstractSocketIoTest.key
            language = testData.englishLanguage
          }
        )
      },
      {
        assertThatJson(it.toString()).apply {
          node("id").isValidId
          node("text").isEqualTo("created translation")
          node("languageTag").isEqualTo("en")
        }
      }
    )
  }
}
