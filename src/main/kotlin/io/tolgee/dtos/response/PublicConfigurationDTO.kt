package io.tolgee.dtos.response

import io.swagger.v3.oas.annotations.media.Schema
import io.tolgee.configuration.tolgee.TolgeeProperties
import io.tolgee.util.VersionProvider

@Suppress("MemberVisibilityCanBePrivate", "unused")
class PublicConfigurationDTO(
  @Schema(hidden = true)
  properties: TolgeeProperties
) {

  val authentication: Boolean = properties.authentication.enabled
  var authMethods: AuthMethodsDTO? = null
  val passwordResettable: Boolean
  val allowRegistrations: Boolean
  val screenshotsUrl = properties.screenshotsUrl
  val maxUploadFileSize = properties.maxUploadFileSize
  val clientSentryDsn = properties.sentry.clientDsn
  val needsEmailVerification = properties.authentication.needsEmailVerification
  val userCanCreateProjects = properties.authentication.userCanCreateProjects
  val userCanCreateOrganizations = properties.authentication.userCanCreateOrganizations
  val socket = SocketIo(
    enabled = properties.socketIo.enabled,
    port = properties.socketIo.port,
    serverUrl = properties.socketIo.externalUrl,
    allowedTransports = properties.socketIo.allowedTransports
  )
  val appName = properties.appName
  val version: String = VersionProvider.version
  val showVersion: Boolean = properties.internal.showVersion
  val maxTranslationTextLength: Long = properties.maxTranslationTextLength
  val recaptchaSiteKey = properties.recaptcha.siteKey

  class AuthMethodsDTO(val github: GithubPublicConfigDTO)
  data class GithubPublicConfigDTO(val clientId: String?) {
    val enabled: Boolean = clientId != null && clientId.isNotEmpty()
  }

  data class SocketIo(val enabled: Boolean, val port: Int, val serverUrl: String?, val allowedTransports: List<String>)

  init {
    if (authentication) {
      authMethods = AuthMethodsDTO(GithubPublicConfigDTO(properties.authentication.github.clientId))
    }
    passwordResettable = properties.authentication.nativeEnabled
    allowRegistrations = properties.authentication.registrationsAllowed
  }
}
