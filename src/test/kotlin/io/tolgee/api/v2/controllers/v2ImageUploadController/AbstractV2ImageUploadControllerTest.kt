/*
 * Copyright (c) 2020. Tolgee
 */

package io.tolgee.api.v2.controllers.v2ImageUploadController

import io.tolgee.controllers.SignedInControllerTest
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.ResultActions
import org.testng.annotations.AfterClass
import java.io.File

abstract class AbstractV2ImageUploadControllerTest : SignedInControllerTest() {
  @Value("classpath:screenshot.png")
  lateinit var screenshotFile: Resource

  @AfterClass
  fun cleanUp() {
    File("${tolgeeProperties.fileStorage.fsDataPath}/uploadedImages").deleteRecursively()
  }

  protected fun performStoreImage(): ResultActions {
    return performAuthMultipart(
      url = "/v2/image-upload",
      files = listOf(
        MockMultipartFile(
          "image", "originalShot.png", "image/png",
          screenshotFile.file.readBytes()
        )
      )
    )
  }
}
