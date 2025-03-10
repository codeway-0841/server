package io.tolgee.dtos.request

import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

data class TagKeyDto(
  @field:NotBlank
  @field:Size(max = 100)
  val name: String = ""
)
