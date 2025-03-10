/*
 * Copyright (c) 2020. Tolgee
 */

package io.tolgee.api.v2.controllers.v2ImageUploadController

import io.tolgee.annotations.ProjectJWTAuthTestMethod
import io.tolgee.assertions.Assertions.assertThat
import io.tolgee.component.TimestampValidation
import io.tolgee.fixtures.andAssertThatJson
import io.tolgee.fixtures.andIsCreated
import io.tolgee.fixtures.andIsOk
import io.tolgee.fixtures.andPrettyPrint
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testng.annotations.Test
import java.io.File
import java.util.*

@SpringBootTest(
  properties = [
    "tolgee.authentication.secured-image-retrieval=true",
    "tolgee.authentication.secured-image-timestamp-max-age=10000"
  ]
)
class SecuredV2ImageUploadControllerTest : AbstractV2ImageUploadControllerTest() {
  @set:Autowired
  lateinit var timestampValidation: TimestampValidation

  @Test
  fun getScreenshotFileNoTimestamp() {
    val image = imageUploadService.store(screenshotFile, userAccount!!)

    val result = performAuthGet("/uploaded-images/${image.filename}.jpg")
      .andExpect(status().isBadRequest)
      .andReturn()

    assertThat(result).error().isCustomValidation.hasMessage("invalid_timestamp")
  }

  @Test
  fun getScreenshotFileInvalidTimestamp() {
    val image = imageUploadService.store(screenshotFile, userAccount!!)

    val rawTimestamp = Date().time - tolgeeProperties.authentication.securedImageTimestampMaxAge - 500
    val timestamp = timestampValidation.encryptTimeStamp(rawTimestamp)

    val result = performAuthGet("/uploaded-images/${image.filename}.jpg?timestamp=$timestamp")
      .andExpect(status().isBadRequest)
      .andReturn()

    assertThat(result).error().isCustomValidation.hasMessage("invalid_timestamp")
  }

  @Test
  fun getFile() {
    val image = imageUploadService.store(screenshotFile, userAccount!!)

    val rawTimestamp = Date().time - tolgeeProperties.authentication.securedImageTimestampMaxAge + 500
    val timestamp = timestampValidation.encryptTimeStamp(rawTimestamp)

    val storedImage = performAuthGet("/uploaded-images/${image.filename}.jpg?timestamp=$timestamp")
      .andIsOk.andReturn().response.contentAsByteArray
    val file = File(tolgeeProperties.fileStorage.fsDataPath + "/uploadedImages/" + image.filename + ".jpg")
    assertThat(storedImage).isEqualTo(file.readBytes())
  }

  @Test
  @ProjectJWTAuthTestMethod
  fun upload() {
    performStoreImage().andPrettyPrint.andIsCreated.andAssertThatJson {
      node("filename").isString.satisfies {
        val file = File(tolgeeProperties.fileStorage.fsDataPath + "/uploadedImages/" + it + ".jpg")
        assertThat(file).exists()
        assertThat(file.readBytes().size).isLessThan(1024 * 100)
      }
      node("requestFilename").isString.satisfies {
        timestampValidation.checkTimeStamp(it.split("timestamp=")[1])
      }
    }
  }
}
