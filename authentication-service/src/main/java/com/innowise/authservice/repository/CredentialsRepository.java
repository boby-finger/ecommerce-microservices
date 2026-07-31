package com.innowise.authservice.repository;

import com.innowise.authservice.model.Credentials;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CredentialsRepository extends JpaRepository<Credentials, UUID> {

    Optional<Credentials> findByUsername(String username);

    boolean existsByUsername(String username);
}