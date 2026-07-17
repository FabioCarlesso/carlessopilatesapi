package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.PasswordResetToken;
import com.carlesso.pilatesapi.entity.User;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :agora where t.user = :user and t.usedAt is null")
    void invalidarTokensAtivos(@Param("user") User user, @Param("agora") LocalDateTime agora);
}
