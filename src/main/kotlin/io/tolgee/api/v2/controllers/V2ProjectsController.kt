/*
 * Copyright (c) 2020. Tolgee
 */

package io.tolgee.api.v2.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import io.tolgee.api.v2.hateoas.project.*
import io.tolgee.api.v2.hateoas.user_account.UserAccountInProjectModel
import io.tolgee.api.v2.hateoas.user_account.UserAccountInProjectModelAssembler
import io.tolgee.configuration.tolgee.TolgeeProperties
import io.tolgee.constants.Message
import io.tolgee.dtos.request.CreateProjectDTO
import io.tolgee.dtos.request.EditProjectDTO
import io.tolgee.dtos.request.ProjectInviteUserDto
import io.tolgee.exceptions.BadRequestException
import io.tolgee.exceptions.NotFoundException
import io.tolgee.exceptions.PermissionException
import io.tolgee.model.Permission
import io.tolgee.model.UserAccount
import io.tolgee.model.views.ProjectView
import io.tolgee.model.views.ProjectWithStatsView
import io.tolgee.model.views.UserAccountInProjectView
import io.tolgee.security.AuthenticationFacade
import io.tolgee.security.api_key_auth.AccessWithApiKey
import io.tolgee.security.project_auth.AccessWithAnyProjectPermission
import io.tolgee.security.project_auth.AccessWithProjectPermission
import io.tolgee.security.project_auth.ProjectHolder
import io.tolgee.service.*
import org.springdoc.api.annotations.ParameterObject
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedResourcesAssembler
import org.springframework.hateoas.CollectionModel
import org.springframework.hateoas.MediaTypes
import org.springframework.hateoas.PagedModel
import org.springframework.web.bind.annotation.*
import javax.validation.Valid

@Suppress("MVCPathVariableInspection")
@RestController
@CrossOrigin(origins = ["*"])
@RequestMapping(value = ["/v2/projects"])
@Tag(name = "Projects")
class V2ProjectsController(
  private val projectService: ProjectService,
  private val projectHolder: ProjectHolder,
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  private val arrayResourcesAssembler: PagedResourcesAssembler<ProjectView>,
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  private val userArrayResourcesAssembler: PagedResourcesAssembler<UserAccountInProjectView>,
  private val userAccountInProjectModelAssembler: UserAccountInProjectModelAssembler,
  private val projectModelAssembler: ProjectModelAssembler,
  private val projectWithStatsModelAssembler: ProjectWithStatsModelAssembler,
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  private val arrayWithStatsResourcesAssembler: PagedResourcesAssembler<ProjectWithStatsView>,
  private val userAccountService: UserAccountService,
  private val permissionService: PermissionService,
  private val authenticationFacade: AuthenticationFacade,
  private val tolgeeProperties: TolgeeProperties,
  private val securityService: SecurityService,
  private val invitationService: InvitationService,
  private val organizationService: OrganizationService,
  private val organizationRoleService: OrganizationRoleService
) {
  @Operation(summary = "Returns all projects where current user has any permission")
  @GetMapping("", produces = [MediaTypes.HAL_JSON_VALUE])
  fun getAll(
    @ParameterObject pageable: Pageable,
    @RequestParam("search") search: String?
  ): PagedModel<ProjectModel> {
    val projects = projectService.findPermittedPaged(pageable, search)
    return arrayResourcesAssembler.toModel(projects, projectModelAssembler)
  }

  @Operation(summary = "Returns all projects (includingStatistics) where current user has any permission")
  @GetMapping("/with-stats", produces = [MediaTypes.HAL_JSON_VALUE])
  fun getAllWithStatistics(
    @ParameterObject pageable: Pageable,
    @RequestParam("search") search: String?
  ): PagedModel<ProjectWithStatsModel> {
    val projects = projectService.findPermittedPaged(pageable, search)
    val projectIds = projects.content.map { it.id }
    val stats = projectService.getProjectsStatistics(projectIds).associateBy { it.projectId }
    val languages = projectService.getProjectsWithFetchedLanguages(projectIds)
      .associate { it.id to it.languages.toList() }
    val projectsWithStatsContent = projects.content.map { ProjectWithStatsView(it, stats[it.id]!!, languages[it.id]!!) }
    val page = PageImpl(projectsWithStatsContent, projects.pageable, projects.totalElements)
    return arrayWithStatsResourcesAssembler.toModel(page, projectWithStatsModelAssembler)
  }

  @GetMapping("/{projectId}")
  @AccessWithAnyProjectPermission
  @AccessWithApiKey
  @Operation(summary = "Returns project by id")
  fun get(@PathVariable("projectId") projectId: Long): ProjectModel {
    return projectService.getView(projectId)?.let {
      projectModelAssembler.toModel(it)
    } ?: throw NotFoundException()
  }

  @GetMapping("/{projectId}/users")
  @Operation(summary = "Returns project all users, who have permission to access project")
  @AccessWithProjectPermission(Permission.ProjectPermissionType.MANAGE)
  fun getAllUsers(
    @PathVariable("projectId") projectId: Long,
    @ParameterObject pageable: Pageable,
    @RequestParam("search", required = false) search: String?
  ): PagedModel<UserAccountInProjectModel> {
    return userAccountService.getAllInProject(projectId, pageable, search).let { users ->
      userArrayResourcesAssembler.toModel(users, userAccountInProjectModelAssembler)
    }
  }

  @PutMapping("/{projectId}/users/{userId}/set-permissions/{permissionType}")
  @AccessWithProjectPermission(Permission.ProjectPermissionType.MANAGE)
  @Operation(summary = "Sets user's direct permission")
  fun setUsersPermissions(
    @PathVariable("projectId") projectId: Long,
    @PathVariable("userId") userId: Long,
    @PathVariable("permissionType") permissionType: Permission.ProjectPermissionType,
  ) {
    if (userId == authenticationFacade.userAccount.id) {
      throw BadRequestException(Message.CANNOT_SET_YOUR_OWN_PERMISSIONS)
    }
    permissionService.setUserDirectPermission(projectId, userId, permissionType)
  }

  @PutMapping("/{projectId}/users/{userId}/revoke-access")
  @AccessWithProjectPermission(Permission.ProjectPermissionType.MANAGE)
  @Operation(summary = "Revokes user's access")
  fun revokePermission(
    @PathVariable("projectId") projectId: Long,
    @PathVariable("userId") userId: Long
  ) {
    if (userId == authenticationFacade.userAccount.id) {
      throw BadRequestException(Message.CAN_NOT_REVOKE_OWN_PERMISSIONS)
    }
    permissionService.revoke(projectId, userId)
  }

  @PostMapping(value = [""])
  @Operation(summary = "Creates project with specified languages")
  fun createProject(@RequestBody @Valid dto: CreateProjectDTO): ProjectModel {
    val userAccount = authenticationFacade.userAccount
    if (!this.tolgeeProperties.authentication.userCanCreateProjects &&
      userAccount.role != UserAccount.Role.ADMIN
    ) {
      throw PermissionException()
    }
    val project = projectService.createProject(dto)
    return projectModelAssembler.toModel(projectService.getView(project.id)!!)
  }

  @Operation(summary = "Modifies project")
  @PutMapping(value = ["/{projectId}"])
  @AccessWithProjectPermission(Permission.ProjectPermissionType.MANAGE)
  fun editProject(@RequestBody @Valid dto: EditProjectDTO): ProjectModel {
    val project = projectService.editProject(projectHolder.project.id, dto)
    return projectModelAssembler.toModel(projectService.getView(project.id)!!)
  }

  @DeleteMapping(value = ["/{projectId}"])
  @Operation(summary = "Deletes project by id")
  fun deleteProject(@PathVariable projectId: Long) {
    securityService.checkProjectPermission(projectId, Permission.ProjectPermissionType.MANAGE)
    projectService.deleteProject(projectId)
  }

  @PutMapping(value = ["/{projectId:[0-9]+}/transfer-to-organization/{organizationId:[0-9]+}"])
  @Operation(summary = "Transfers project's ownership to organization")
  @AccessWithProjectPermission(Permission.ProjectPermissionType.MANAGE)
  fun transferProjectToOrganization(@PathVariable projectId: Long, @PathVariable organizationId: Long) {
    organizationRoleService.checkUserIsOwner(organizationId)
    projectService.transferToOrganization(projectId, organizationId)
  }

  @PutMapping(value = ["/{projectId:[0-9]+}/transfer-to-user/{userId:[0-9]+}"])
  @Operation(summary = "Transfers project's ownership to user")
  @AccessWithProjectPermission(Permission.ProjectPermissionType.MANAGE)
  fun transferProjectToUser(@PathVariable projectId: Long, @PathVariable userId: Long) {
    securityService.checkAnyProjectPermission(projectId, userId)
    projectService.transferToUser(projectId, userId)
  }

  @PutMapping(value = ["/{projectId:[0-9]+}/leave"])
  @Operation(summary = "Leave project")
  @AccessWithProjectPermission(Permission.ProjectPermissionType.VIEW)
  fun leaveProject(@PathVariable projectId: Long) {
    val project = projectHolder.projectEntity
    if (project.userOwner?.id == authenticationFacade.userAccount.id) {
      throw BadRequestException(Message.CANNOT_LEAVE_OWNING_PROJECT)
    }
    val permissionData = permissionService.getProjectPermissionData(project.id, authenticationFacade.userAccount.id)
    if (permissionData.organizationRole != null) {
      throw BadRequestException(Message.CANNOT_LEAVE_PROJECT_WITH_ORGANIZATION_ROLE)
    }

    if (permissionData.directPermissions == null) {
      throw BadRequestException(Message.DONT_HAVE_DIRECT_PERMISSIONS)
    }

    val permissionEntity = permissionService.findById(permissionData.directPermissions.id)
      ?: throw NotFoundException()

    permissionService.delete(permissionEntity)
  }

  @AccessWithProjectPermission(Permission.ProjectPermissionType.MANAGE)
  @Operation(summary = "Returns transfer option")
  @GetMapping(value = ["/{projectId:[0-9]+}/transfer-options"])
  fun getTransferOptions(@RequestParam search: String? = ""): CollectionModel<ProjectTransferOptionModel> {
    val project = projectHolder.project
    val organizations = organizationService.findPermittedPaged(
      PageRequest.of(0, 10),
      true,
      search,
      project.organizationOwnerId
    )
    val options = organizations.content.map {
      ProjectTransferOptionModel(
        name = it.name,
        id = it.id,
        type = ProjectTransferOptionModel.TransferOptionType.ORGANIZATION
      )
    }.toMutableList()
    val users = userAccountService.getAllInProject(
      projectId = project.id,
      PageRequest.of(0, 10),
      search,
      project.userOwnerId
    )
    options.addAll(
      users.content.map {
        ProjectTransferOptionModel(
          name = it.name,
          username = it.username,
          id = it.id,
          type = ProjectTransferOptionModel.TransferOptionType.USER
        )
      }
    )
    options.sortBy { it.name }
    return CollectionModel.of(options)
  }

  @PutMapping("/{projectId}/invite")
  @Operation(summary = "Generates user invitation link for project")
  @AccessWithProjectPermission(Permission.ProjectPermissionType.MANAGE)
  fun inviteUser(@RequestBody @Valid invitation: ProjectInviteUserDto): String {
    val project = projectService.get(projectHolder.project.id).orElseThrow { NotFoundException() }!!
    return invitationService.create(project, invitation.type!!)
  }
}
