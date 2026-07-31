package com.innowise.authservice;

import com.innowise.authservice.user.UserServiceClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @MockitoBean
    protected UserServiceClient userServiceClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        KeyPair keyPair = generateRsaKeyPair();
        Path directory = createTempDirectory();
        String privateKey = writePem(directory.resolve("private.pem"),
                "PRIVATE KEY", keyPair.getPrivate().getEncoded());
        String publicKey = writePem(directory.resolve("public.pem"),
                "PUBLIC KEY", keyPair.getPublic().getEncoded());

        registry.add("app.rsa.private-key", () -> privateKey);
        registry.add("app.rsa.public-key", () -> publicKey);
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA is not available", e);
        }
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("auth-service-test-keys");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create a directory for test keys", e);
        }
    }

    private static String writePem(Path path, String type, byte[] encodedKey) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(encodedKey);
        String pem = "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
        try {
            Files.writeString(path, pem, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write the test key to " + path, e);
        }
        return path.toUri().toString();
    }
}