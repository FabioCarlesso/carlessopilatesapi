package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.PreferenciasUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreferenciasUsuarioRepository extends JpaRepository<PreferenciasUsuario, Long> {

    Optional<PreferenciasUsuario> findByUserId(Long userId);
}
