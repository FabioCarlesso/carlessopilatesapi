package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :agora where t.familyId = :familyId and t.revokedAt is null")
    int revogarFamilia(@Param("familyId") UUID familyId, @Param("agora") LocalDateTime agora);

    /**
     * Remove cadeias que já não autorizam nada: as que passaram do teto absoluto
     * da sessão (todo token da família vence até ele) e as revogadas antes de
     * {@code limiteRevogados}, mantidas por um tempo só para investigação.
     */
    @Modifying
    @Query("delete from RefreshToken t where t.familyExpiresAt < :agora or t.revokedAt < :limiteRevogados")
    int excluirObsoletos(@Param("agora") LocalDateTime agora, @Param("limiteRevogados") LocalDateTime limiteRevogados);
}
