package io.tolgee.api.v2.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import io.tolgee.api.v2.hateoas.key.KeyModel
import io.tolgee.api.v2.hateoas.key.KeyModelAssembler
import io.tolgee.api.v2.hateoas.key.KeyWithDataModel
import io.tolgee.api.v2.hateoas.key.KeyWithDataModelAssembler
import io.tolgee.constants.Message
import io.tolgee.controllers.IController
import io.tolgee.dtos.request.ComplexEditKeyDto
import io.tolgee.dtos.request.CreateKeyDto
import io.tolgee.dtos.request.EditKeyDto
import io.tolgee.exceptions.BadRequestException
import io.tolgee.exceptions.NotFoundException
import io.tolgee.model.Permission
import io.tolgee.model.Project
import io.tolgee.model.enums.ApiScope
import io.tolgee.model.key.Key
import io.tolgee.security.AuthenticationFacade
import io.tolgee.security.api_key_auth.AccessWithApiKey
import io.tolgee.security.project_auth.AccessWithProjectPermission
import io.tolgee.security.project_auth.ProjectHolder
import io.tolgee.service.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import javax.validation.Valid

@Suppress("MVCPathVariableInspection")
@RestController
@CrossOrigin(origins = ["*"])
@RequestMapping(
  value = [
    "/v2/projects/{projectId}/keys",
    "/v2/projects/keys"
  ]
)
@Tag(name = "Localization keys", description = "Manipulates localization keys and their translations and metadata")
class V2KeyController(
  private val keyService: KeyService,
  private val projectHolder: ProjectHolder,
  private val keyModelAssembler: KeyModelAssembler,
  private val keyWithDataModelAssembler: KeyWithDataModelAssembler,
  private val securityService: SecurityService,
  private val authenticationFacade: AuthenticationFacade,
  private val translationService: TranslationService,
  private val tagService: TagService,
  private val screenshotService: ScreenshotService
) : IController {
  @PostMapping(value = ["/create", ""])
  @AccessWithProjectPermission(Permission.ProjectPermissionType.EDIT)
  @AccessWithApiKey(scopes = [ApiScope.KEYS_EDIT])
  @Operation(summary = "Creates new key")
  @ResponseStatus(HttpStatus.CREATED)
  fun create(@RequestBody @Valid dto: CreateKeyDto): ResponseEntity<KeyWithDataModel> {
    if (dto.screenshotUploadedImageIds != null) {
      projectHolder.projectEntity.checkScreenshotsUploadPermission()
    }
    val key = keyService.create(projectHolder.projectEntity, dto)
    return ResponseEntity(keyWithDataModelAssembler.toModel(key), HttpStatus.CREATED)
  }

  @PutMapping(value = ["/{id}/complex-update"])
  @Operation(summary = "More")
  @AccessWithProjectPermission(Permission.ProjectPermissionType.TRANSLATE)
  // key permissions are checked separately in method body
  @AccessWithApiKey([ApiScope.TRANSLATIONS_EDIT])
  @Transactional
  fun complexEdit(@PathVariable id: Long, @RequestBody @Valid dto: ComplexEditKeyDto): KeyWithDataModel {
    val key = keyService.get(id).orElseThrow { NotFoundException() }
    key.checkInProject()
    var editPermissionsChecked = false

    dto.translations?.let {
      translationService.setForKey(key, translations = dto.translations)
    }

    dto.tags?.also {
      // check whether there is a change
      if (key.keyMeta?.tags?.map { it.name }?.containsAll(dto.tags) != true &&
        dto.tags.containsAll(key.keyMeta?.tags?.map { it.name } ?: listOf()) &&
        !editPermissionsChecked
      ) {
        key.project?.checkKeysEditPermission()
        editPermissionsChecked = true
      }
      // if provided, remove deleted tags
      key.keyMeta?.tags?.forEach { oldTag ->
        // delete all other tags
        if (dto.tags.find { oldTag.name == it } == null) {
          tagService.remove(key, oldTag)
        }
      }
    }?.forEach { tagName ->
      tagService.tagKey(key, tagName)
    }

    if (key.name != dto.name) {
      key.project?.checkKeysEditPermission()
      editPermissionsChecked = true
    }

    dto.screenshotIdsToDelete?.let { screenshotIds ->
      if (screenshotIds.isNotEmpty()) {
        key.project?.checkScreenshotsDeletePermission()
        editPermissionsChecked = true
      }
      val screenshots = screenshotService.findByIdIn(screenshotIds).onEach {
        if (it.key.id != key.id) {
          throw BadRequestException(Message.SCREENSHOT_NOT_OF_KEY)
        }
      }
      screenshotService.delete(screenshots)
    }

    dto.screenshotUploadedImageIds?.let {
      key.project?.checkScreenshotsUploadPermission()
      screenshotService.saveUploadedImages(it, key)
    }

    return keyWithDataModelAssembler.toModel(keyService.edit(key, dto.name))
  }

  @PutMapping(value = ["/{id}"])
  @Operation(summary = "Edits key name")
  @AccessWithProjectPermission(Permission.ProjectPermissionType.EDIT)
  @AccessWithApiKey(scopes = [ApiScope.KEYS_EDIT])
  fun edit(@PathVariable id: Long, @RequestBody @Valid dto: EditKeyDto): KeyModel {
    val key = keyService.get(id).orElseThrow { NotFoundException() }
    key.checkInProject()
    return keyService.edit(id, dto).model
  }

  @DeleteMapping(value = ["/{ids:[0-9,]+}"])
  @Transactional
  @Operation(summary = "Deletes one or multiple keys by their IDs")
  @AccessWithProjectPermission(Permission.ProjectPermissionType.EDIT)
  @AccessWithApiKey(scopes = [ApiScope.KEYS_EDIT])
  fun delete(@PathVariable ids: Set<Long>) {
    keyService.get(ids).forEach { it.checkInProject() }
    keyService.deleteMultiple(ids)
  }

  private fun Key.checkInProject() {
    keyService.checkInProject(this, projectHolder.project.id)
  }

  private val Key.model: KeyModel
    get() = keyModelAssembler.toModel(this)

  private fun Project.checkKeysEditPermission() {
    if (authenticationFacade.isApiKeyAuthentication) {
      securityService.checkApiKeyScopes(setOf(ApiScope.KEYS_EDIT), authenticationFacade.apiKey)
    }
    securityService.checkProjectPermission(this.id, Permission.ProjectPermissionType.EDIT)
  }

  private fun Project.checkScreenshotsDeletePermission() {
    if (authenticationFacade.isApiKeyAuthentication) {
      securityService.checkApiKeyScopes(setOf(ApiScope.SCREENSHOTS_DELETE), authenticationFacade.apiKey)
    }
    securityService.checkProjectPermission(this.id, Permission.ProjectPermissionType.TRANSLATE)
  }

  private fun Project.checkScreenshotsUploadPermission() {
    if (authenticationFacade.isApiKeyAuthentication) {
      securityService.checkApiKeyScopes(setOf(ApiScope.SCREENSHOTS_UPLOAD), authenticationFacade.apiKey)
    }
    securityService.checkProjectPermission(this.id, Permission.ProjectPermissionType.TRANSLATE)
  }
}
