package com.techpulse.taskmanagement.service;

import com.techpulse.taskmanagement.dto.request.LoginRequest;
import com.techpulse.taskmanagement.dto.response.LoginResponse;
import com.techpulse.taskmanagement.entity.User;
import com.techpulse.taskmanagement.repository.UserRepository;
import com.techpulse.taskmanagement.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository, JwtService jwtService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = TenantService.normalizeEmail(request.email());
        try {
            authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                    email, request.password()));
        } catch (AuthenticationException exception) {
            throw new BadCredentialsException("Invalid email or password");
        }
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return new LoginResponse("Bearer", jwtService.createToken(user), jwtService.getExpirationSeconds());
    }
}
