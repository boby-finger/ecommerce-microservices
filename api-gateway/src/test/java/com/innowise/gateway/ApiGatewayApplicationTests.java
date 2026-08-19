package com.innowise.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
		"services.internal-api-key=test-key",
		"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8002/oauth2/jwks"
})
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
