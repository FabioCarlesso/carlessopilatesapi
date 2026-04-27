package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.UserRequestDTO;
import com.carlesso.pilatesapi.dto.UserResponseDTO;
import com.carlesso.pilatesapi.dto.UserUpdateDTO;
import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponseDTO criar(UserRequestDTO dto) {
        String email = normalizarEmail(dto.email());
        if (repository.existsByEmail(email)) {
            throw new ConflictException("E-mail já cadastrado");
        }

        User user = new User();
        user.setName(dto.name());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setRole(dto.role());

        return UserResponseDTO.from(repository.save(user));
    }

    @Transactional(readOnly = true)
    public Page<UserResponseDTO> listar(Pageable pageable) {
        return repository.findAll(pageable).map(UserResponseDTO::from);
    }

    @Transactional(readOnly = true)
    public UserResponseDTO buscarPorId(Long id) {
        return UserResponseDTO.from(encontrar(id));
    }

    @Transactional(readOnly = true)
    public UserResponseDTO buscarPorEmail(String email) {
        return UserResponseDTO.from(
                repository.findByEmail(email.toLowerCase(Locale.ROOT))
                        .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"))
        );
    }

    @Transactional
    public UserResponseDTO atualizar(Long id, UserUpdateDTO dto, String currentEmail) {
        User user = encontrar(id);

        if (user.getEmail().equals(currentEmail) && dto.role() != null && !dto.role().equals(user.getRole())) {
            throw new BusinessException("Não é possível alterar o próprio perfil de acesso");
        }

        if (dto.email() != null) {
            String email = normalizarEmail(dto.email());
            if (repository.existsByEmailAndIdNot(email, id)) {
                throw new ConflictException("E-mail já cadastrado");
            }
            user.setEmail(email);
        }
        if (dto.name() != null) user.setName(dto.name());
        if (dto.password() != null) user.setPassword(passwordEncoder.encode(dto.password()));
        if (dto.role() != null) user.setRole(dto.role());

        return UserResponseDTO.from(repository.save(user));
    }

    @Transactional
    public void excluir(Long id, String currentEmail) {
        User user = encontrar(id);
        if (user.getEmail().equals(currentEmail)) {
            throw new BusinessException("Não é possível excluir a própria conta");
        }
        repository.delete(user);
    }

    private User encontrar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: " + id));
    }

    private String normalizarEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }
}
