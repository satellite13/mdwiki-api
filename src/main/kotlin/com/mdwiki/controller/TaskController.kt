package com.mdwiki.controller

import com.mdwiki.dto.CompleteOpenTaskRequest
import com.mdwiki.dto.OpenTaskResponse
import com.mdwiki.service.OpenTaskService
import com.mdwiki.service.usecase.CompleteOpenTaskUseCase
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/tasks")
class TaskController(
    private val openTaskService: OpenTaskService,
    private val completeOpenTaskUseCase: CompleteOpenTaskUseCase
) {

    @GetMapping("/open")
    fun listOpenTasks(auth: Authentication): List<OpenTaskResponse> =
        openTaskService.listOpenTasks(auth.name)

    @PostMapping("/complete")
    fun completeTask(@Valid @RequestBody request: CompleteOpenTaskRequest, auth: Authentication) {
        completeOpenTaskUseCase.execute(request, auth.name)
    }
}
