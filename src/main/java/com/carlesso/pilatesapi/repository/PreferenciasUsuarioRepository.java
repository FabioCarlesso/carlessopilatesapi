package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.PreferenciasUsuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreferenciasUsuarioRepository extends JpaRepository<PreferenciasUsuario, Long> {

    Optional<PreferenciasUsuario> findByUserId(Long userId);

    Optional<PreferenciasUsuario> findByUserEmail(String email);
}
