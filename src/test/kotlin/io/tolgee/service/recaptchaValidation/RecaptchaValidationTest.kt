/*
 * Copyright (c) 2020. Tolgee
 */

package io.tolgee.service.recaptchaValidation

import io.tolgee.AbstractSpringTest
import io.tolgee.assertions.Assertions.assertThat
import io.tolgee.security.ReCaptchaValidationService
import io.tolgee.security.ReCaptchaValidationService.Companion
import org.mockito.Mockito
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.util.*

@SpringBootTest(
  properties = [
    "tolgee.recaptcha.secretKey=dummy_key",
  ]
)
class RecaptchaValidationTest : AbstractSpringTest() {

  @Autowired
  lateinit var reCaptchaValidationService: ReCaptchaValidationService

  @Autowired
  @MockBean
  lateinit var restTemplate: RestTemplate

  @BeforeMethod
  fun setup() {
    Mockito.reset(restTemplate)
    whenever(
      restTemplate.postForEntity(any() as String, any(), any() as Class<Companion.Response>)
    ).then {
      ResponseEntity(
        Companion.Response().apply {
          success = true
          challengeTs = Date()
          hostname = ""
          errorCodes = null
        },
        HttpStatus.OK
      )
    }
  }

  @Test
  fun `validates token`() {
    assertThat(reCaptchaValidationService.validate("dummy_token", "10.10.10.10")).isEqualTo(true)

    verify(restTemplate, times(1))
      .postForEntity(
        eq("https://www.google.com/recaptcha/api/siteverify"), any(), eq(Companion.Response::class.java)
      )
  }

  @Test
  fun `returns false when invalid`() {
    whenever(
      restTemplate.postForEntity(any() as String, any(), any() as Class<Companion.Response>)
    ).then {
      ResponseEntity(
        Companion.Response().apply {
          success = false
          challengeTs = Date()
          hostname = ""
          errorCodes = null
        },
        HttpStatus.OK
      )
    }

    assertThat(reCaptchaValidationService.validate("dummy_token", "10.10.10.10")).isEqualTo(false)

    verify(restTemplate, times(1))
      .postForEntity(
        eq("https://www.google.com/recaptcha/api/siteverify"), any(), eq(Companion.Response::class.java)
      )
  }
}
