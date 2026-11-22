package com.techpulse.taskmanagement.service;

import com.techpulse.taskmanagement.dto.request.AssignTaskRequest;
import com.techpulse.taskmanagement.dto.request.CreateTaskRequest;
import com.techpulse.taskmanagement.dto.request.UpdateTaskStatusRequest;
import com.techpulse.taskmanagement.dto.response.TaskResponse;
import com.techpulse.taskmanagement.entity.Task;
import com.techpulse.taskmanagement.enums.Role;
import com.techpulse.taskmanagement.enums.TaskStatus;
import com.techpulse.taskmanagement.exception.ResourceNotFoundException;
import com.techpulse.taskmanagement.repository.TaskRepository;
import com.techpulse.taskmanagement.repository.TenantRepository;
import com.techpulse.taskmanagement.repository.UserRepository;
import com.techpulse.taskmanagement.security.AuthenticatedUser;
import com.techpulse.taskmanagement.security.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskService {
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository,
                       TenantRepository tenantRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public TaskResponse create(CreateTaskRequest request, AuthenticatedUser caller) {
        Long tenantId = currentTenant(caller);
        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant"));
        Task task = taskRepository.save(new Task(request.title().trim(), request.description(),
                TaskStatus.TODO, tenant));
        return toResponse(task);
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public TaskResponse assign(Long taskId, AssignTaskRequest request, AuthenticatedUser caller) {
        Long tenantId = currentTenant(caller);
        Task task = taskRepository.findByIdAndTenant_Id(taskId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Task"));
        var assignee = userRepository.findByIdAndTenant_Id(request.userId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User"));
        task.assignTo(assignee);
        taskRepository.save(task);
        return toResponse(task, assignee);
    }

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public TaskResponse updateStatus(Long taskId, UpdateTaskStatusRequest request, AuthenticatedUser caller) {
        Long tenantId = currentTenant(caller);
        Task task = taskRepository.findByIdAndTenant_Id(taskId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Task"));
        if (task.getAssignedTo() == null || !task.getAssignedTo().getId().equals(caller.userId())) {
            throw new ResourceNotFoundException("Task");
        }
        task.updateStatus(request.status());
        return toResponse(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listFor(AuthenticatedUser caller) {
        Long tenantId = currentTenant(caller);
        List<Task> tasks = caller.role() == Role.USER
                ? taskRepository.findAllByTenant_IdAndAssignedTo_Id(tenantId, caller.userId())
                : taskRepository.findAllByTenant_Id(tenantId);
        return tasks.stream().map(this::toResponse).toList();
    }

    private Long currentTenant(AuthenticatedUser caller) {
        Long tenantId = TenantContext.requireTenantId();
        if (!tenantId.equals(caller.tenantId())) {
            throw new IllegalStateException("Authenticated tenant context mismatch");
        }
        return tenantId;
    }

    private TaskResponse toResponse(Task task) {
        return toResponse(task, task.getAssignedTo());
    }

    private TaskResponse toResponse(Task task, com.techpulse.taskmanagement.entity.User assignee) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getStatus(),
                assignee == null ? null : assignee.getId(),
                assignee == null ? null : assignee.getEmail(),
                task.getTenant().getId());
    }
}
