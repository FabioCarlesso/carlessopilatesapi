package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.AuthLoginRequestDTO;
import com.carlesso.pilatesapi.dto.AuthResponseDTO;
import com.carlesso.pilatesapi.dto.UserResponseDTO;
import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.exception.TooManyRequestsException;
import com.carlesso.pilatesapi.util.EmailNormalizer;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       LoginAttemptService loginAttemptService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
    }

    @Transactional(readOnly = true)
    public AuthResponseDTO login(AuthLoginRequestDTO dto) {
        String email = EmailNormalizer.normalizar(dto.email());
        if (loginAttemptService.isBlocked(email)) {
            throw new TooManyRequestsException("Muitas tentativas. Tente novamente em 15 minutos.");
        }
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, dto.password()));
            loginAttemptService.registerSuccess(email);
            User user = (User) auth.getPrincipal();
            String token = jwtService.generateToken(user);
            return AuthResponseDTO.bearer(token, UserResponseDTO.from(user));
        } catch (AuthenticationException e) {
            loginAttemptService.registerFailure(email);
            throw e;
        }
    }
}
