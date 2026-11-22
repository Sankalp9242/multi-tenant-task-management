package com.techpulse.taskmanagement.repository;

import com.techpulse.taskmanagement.entity.User;
import com.techpulse.taskmanagement.enums.Role;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends Repository<User, Long> {
    User save(User user);
    void delete(User user);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    Optional<User> findByIdAndTenant_Id(Long id, Long tenantId);
    List<User> findAllByTenant_Id(Long tenantId);
    List<User> findAllByTenant_IdAndRoleNot(Long tenantId, Role role);
    long countByTenant_IdAndRole(Long tenantId, Role role);
}
