package com.techpulse.taskmanagement.service;

import com.techpulse.taskmanagement.dto.request.TenantRegistrationRequest;
import com.techpulse.taskmanagement.dto.response.TenantRegistrationResponse;
import com.techpulse.taskmanagement.entity.Tenant;
import com.techpulse.taskmanagement.entity.User;
import com.techpulse.taskmanagement.enums.Role;
import com.techpulse.taskmanagement.exception.ConflictException;
import com.techpulse.taskmanagement.exception.InvalidRequestException;
import com.techpulse.taskmanagement.repository.TenantRepository;
import com.techpulse.taskmanagement.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class TenantService {
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TenantService(TenantRepository tenantRepository, UserRepository userRepository,
                         PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public TenantRegistrationResponse register(TenantRegistrationRequest request) {
        String email = normalizeEmail(request.admin().email());
        validatePassword(request.admin().password());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }
        Tenant tenant = tenantRepository.save(new Tenant(request.tenantName().trim()));
        User admin = userRepository.save(new User(email, passwordEncoder.encode(request.admin().password()),
                Role.ADMIN, tenant));
        return new TenantRegistrationResponse(tenant.getId(), tenant.getName(), tenant.getCreatedAt(),
                admin.getId(), admin.getEmail());
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidRequestException("Password must be at most 72 UTF-8 bytes");
        }
    }
}
