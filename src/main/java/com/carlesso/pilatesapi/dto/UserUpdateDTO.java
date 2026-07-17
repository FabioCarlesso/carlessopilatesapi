package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UserUpdateDTO(
        @Size(min = 1) String name, @Email String email, @Size(min = 8) String password, Role role) {}
