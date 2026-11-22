package com.techpulse.taskmanagement.repository;

import com.techpulse.taskmanagement.entity.Task;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends Repository<Task, Long> {
    Task save(Task task);
    Optional<Task> findByIdAndTenant_Id(Long id, Long tenantId);
    List<Task> findAllByTenant_Id(Long tenantId);
    List<Task> findAllByTenant_IdAndAssignedTo_Id(Long tenantId, Long userId);
    boolean existsByTenant_IdAndAssignedTo_Id(Long tenantId, Long userId);
}
