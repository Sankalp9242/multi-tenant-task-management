package com.techpulse.taskmanagement.controller;

import com.techpulse.taskmanagement.dto.request.AssignTaskRequest;
import com.techpulse.taskmanagement.dto.request.CreateTaskRequest;
import com.techpulse.taskmanagement.dto.request.UpdateTaskStatusRequest;
import com.techpulse.taskmanagement.dto.response.TaskResponse;
import com.techpulse.taskmanagement.security.AuthenticatedUser;
import com.techpulse.taskmanagement.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest request,
                               @AuthenticationPrincipal AuthenticatedUser caller) {
        return taskService.create(request, caller);
    }

    @PutMapping("/{id}/assign")
    public TaskResponse assign(@PathVariable Long id, @Valid @RequestBody AssignTaskRequest request,
                               @AuthenticationPrincipal AuthenticatedUser caller) {
        return taskService.assign(id, request, caller);
    }

    @PutMapping("/{id}/status")
    public TaskResponse updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody UpdateTaskStatusRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser caller) {
        return taskService.updateStatus(id, request, caller);
    }

    @GetMapping
    public List<TaskResponse> list(@AuthenticationPrincipal AuthenticatedUser caller) {
        return taskService.listFor(caller);
    }
}
