package com.innowise.authservice.config;

import com.innowise.authservice.dto.UserServiceCreateRequestDto;
import com.innowise.authservice.model.Credentials;
import com.innowise.authservice.model.Role;
import com.innowise.authservice.repository.CredentialsRepository;
import com.innowise.authservice.user.UserServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FirstAdminSeeder implements CommandLineRunner {
    private final CredentialsRepository credentialsRepository;
    private final UserServiceClient userServiceClient;
    private final PasswordEncoder passwordEncoder;


    @org.springframework.beans.factory.annotation.Value("${admin.username}")
    private String adminUsername;

    @org.springframework.beans.factory.annotation.Value("${admin.password}")
    private String adminPassword;

    @org.springframework.beans.factory.annotation.Value("${admin.name}")
    private String adminName;

    @org.springframework.beans.factory.annotation.Value("${admin.surname}")
    private String adminSurname;

    @org.springframework.beans.factory.annotation.Value("${admin.email}")
    private String adminEmail;

    @org.springframework.beans.factory.annotation.Value("${admin.birth_date}")
    @org.springframework.format.annotation.DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate adminBirthDate;

    @Override
    public void run(String... args) {
        if (credentialsRepository.existsByUsername(adminUsername)) {
            return;
        }
        UUID userId = userServiceClient.createUser(new UserServiceCreateRequestDto(
                adminEmail, adminName, adminSurname, adminBirthDate));

        Credentials admin = new Credentials();
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setUserId(userId);
        credentialsRepository.save(admin);
    }
}
