package com.helpagent.action.repository;

import com.helpagent.action.model.entity.OAuthTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OAuthTokenRepository extends JpaRepository<OAuthTokenEntity, String> {

    Optional<OAuthTokenEntity> findByUsername(String username);

    List<OAuthTokenEntity> findByExpiresAtBefore(Instant time);

    long countByExpiresAtIsNullOrExpiresAtAfter(Instant time);
}