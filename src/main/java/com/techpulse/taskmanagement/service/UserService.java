package com.techpulse.taskmanagement.service;

import com.techpulse.taskmanagement.dto.request.CreateUserRequest;
import com.techpulse.taskmanagement.dto.request.UpdateUserRequest;
import com.techpulse.taskmanagement.dto.response.UserResponse;
import com.techpulse.taskmanagement.entity.User;
import com.techpulse.taskmanagement.enums.Role;
import com.techpulse.taskmanagement.exception.ConflictException;
import com.techpulse.taskmanagement.exception.InvalidRequestException;
import com.techpulse.taskmanagement.exception.ResourceNotFoundException;
import com.techpulse.taskmanagement.repository.TaskRepository;
import com.techpulse.taskmanagement.repository.TenantRepository;
import com.techpulse.taskmanagement.repository.UserRepository;
import com.techpulse.taskmanagement.security.AuthenticatedUser;
import com.techpulse.taskmanagement.security.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TaskRepository taskRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, TenantRepository tenantRepository,
                       TaskRepository taskRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.taskRepository = taskRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse create(CreateUserRequest request, AuthenticatedUser caller) {
        Long tenantId = TenantContext.requireTenantId();
        if (!tenantId.equals(caller.tenantId())) {
            throw new IllegalStateException("Authenticated tenant context mismatch");
        }
        String email = TenantService.normalizeEmail(request.email());
        TenantService.validatePassword(request.password());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }
        var tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("Tenant"));
        User user = userRepository.save(new User(email, passwordEncoder.encode(request.password()),
                request.role(), tenant));
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), tenantId);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional(readOnly = true)
    public java.util.List<UserResponse> list(AuthenticatedUser caller) {
        Long tenantId = currentTenant(caller);
        var users = caller.role() == Role.ADMIN
                ? userRepository.findAllByTenant_Id(tenantId)
                : userRepository.findAllByTenant_IdAndRoleNot(tenantId, Role.MANAGER);
        return users.stream().map(user -> toResponse(user, tenantId)).toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Transactional(readOnly = true)
    public UserResponse get(Long userId, AuthenticatedUser caller) {
        Long tenantId = currentTenant(caller);
        User user = findVisibleUser(userId, tenantId, caller.role());
        return toResponse(user, tenantId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse update(Long userId, UpdateUserRequest request, AuthenticatedUser caller) {
        Long tenantId = currentTenant(caller);
        User user = userRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User"));

        if (request.email() != null) {
            String email = TenantService.normalizeEmail(request.email());
            if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, userId)) {
                throw new ConflictException("Email is already registered");
            }
            user.updateEmail(email);
        }
        if (request.password() != null) {
            if (request.password().isBlank()) {
                throw new InvalidRequestException("Password cannot be blank");
            }
            TenantService.validatePassword(request.password());
            user.updatePassword(passwordEncoder.encode(request.password()));
        }
        if (request.role() != null) {
            ensureTenantKeepsAdmin(user, request.role(), tenantId);
            user.updateRole(request.role());
        }
        userRepository.save(user);
        return toResponse(user, tenantId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(Long userId, AuthenticatedUser caller) {
        Long tenantId = currentTenant(caller);
        User user = userRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User"));
        if (user.getRole() == Role.ADMIN && userRepository.countByTenant_IdAndRole(tenantId, Role.ADMIN) <= 1) {
            throw new ConflictException("The tenant must retain at least one ADMIN user");
        }
        if (taskRepository.existsByTenant_IdAndAssignedTo_Id(tenantId, userId)) {
            throw new ConflictException("Reassign this user's tasks before deleting the user");
        }
        userRepository.delete(user);
    }

    private User findVisibleUser(Long userId, Long tenantId, Role callerRole) {
        User user = userRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User"));
        if (callerRole == Role.MANAGER && user.getRole() == Role.MANAGER) {
            throw new ResourceNotFoundException("User");
        }
        return user;
    }

    private void ensureTenantKeepsAdmin(User user, Role targetRole, Long tenantId) {
        if (user.getRole() == Role.ADMIN && targetRole != Role.ADMIN
                && userRepository.countByTenant_IdAndRole(tenantId, Role.ADMIN) <= 1) {
            throw new ConflictException("The tenant must retain at least one ADMIN user");
        }
    }

    private Long currentTenant(AuthenticatedUser caller) {
        Long tenantId = TenantContext.requireTenantId();
        if (!tenantId.equals(caller.tenantId())) {
            throw new IllegalStateException("Authenticated tenant context mismatch");
        }
        return tenantId;
    }

    private UserResponse toResponse(User user, Long tenantId) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), tenantId);
    }
}
