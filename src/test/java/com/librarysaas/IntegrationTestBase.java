package com.librarysaas;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base class for integration tests that require a MySQL Testcontainer.
 * 
 * Uses a SHARED MySQLContainer that is initialized once for the entire test suite.
 * This prevents Spring ApplicationContext caching issues where each test class
 * would cause Testcontainers to start/stop its own container on a different port,
 * but Spring would cache datasource configuration pointing to the previous container.
 * 
 * All test classes extending this base class use the same MySQL container instance
 * via DynamicPropertySource, ensuring consistent JDBC URLs throughout the test suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
public abstract class IntegrationTestBase {

    // Ensure Docker client picks up the named-pipe endpoint used by Docker Desktop
    // Linux engine on Windows. Setting the system property early (before test execution)
    // helps docker-java choose the correct transport when the environment does not already
    // expose DOCKER_HOST to the JVM. This is test-only and will not affect prod.
    static {
        if (System.getProperty("DOCKER_API_VERSION") == null && System.getenv("DOCKER_API_VERSION") == null) {
            System.setProperty("DOCKER_API_VERSION", "1.55");
        }
        if (System.getProperty("docker.api.version") == null) {
            System.setProperty("docker.api.version", "1.55");
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        // Get the shared container that was initialized once at JVM startup
        MySQLContainer<?> container = SharedTestcontainerConfiguration.getSharedContainer();
        
        if (!container.isRunning()) {
            throw new IllegalStateException(
                "MySQL Testcontainer is not running. " +
                "Ensure Docker Desktop is running and the container was successfully started."
            );
        }
        
        String jdbcUrl = container.getJdbcUrl();
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
            throw new IllegalStateException(
                "Invalid JDBC URL from container: " + jdbcUrl + ". " +
                "Ensure the container is properly initialized."
            );
        }
        
        System.out.println("[TestBase] Registering datasource properties for test class:");
        System.out.println("[TestBase]   JDBC URL: " + jdbcUrl);
        System.out.println("[TestBase]   Username: " + container.getUsername());
        
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
        registry.add("spring.datasource.driver-class-name", container::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> "true");
        // When running tests with a real MySQL Testcontainer, override the
        // Hibernate dialect which is set to H2Dialect in
        // src/test/resources/application-test.yml. Without this override
        // Hibernate generates H2/ANSI SQL (e.g. "fetch first 1 rows only")
        // which MySQL rejects. Use MySQL8Dialect for MySQL 8.x.
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQL8Dialect");
    }
}
