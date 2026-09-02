package com.librarysaas;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared MySQL Testcontainer for the entire integration test suite.
 * 
 * This container is initialized once at JVM startup and shared across all
 * test classes. This prevents Testcontainers from starting/stopping a container
 * per test class, which was causing Spring ApplicationContext caching issues:
 * 
 * Problem: Each test class extending IntegrationTestBase would trigger
 * @Testcontainers to start/stop its own container. Spring would cache the
 * ApplicationContext with the first container's JDBC URL. When the second test
 * class ran, a new container would start on a different port, but Spring would
 * reuse the cached context pointing to the dead first container, causing
 * connection failures.
 * 
 * Solution: One container for the entire suite, started once, stopped at JVM shutdown.
 * All test classes share this container via DynamicPropertySource.
 */
public class SharedTestcontainerConfiguration {
    private static final MySQLContainer<?> SHARED_MYSQL_CONTAINER;
    private static final Exception INITIALIZATION_ERROR;

    static {
        MySQLContainer<?> tempContainer = null;
        Exception tempError = null;
        
        try {
            System.out.println("[TestContainer] Initializing shared MySQL container...");
            
            // Initialize the shared container once for the entire test suite
            tempContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.33"))
                    .withDatabaseName("librarydb")
                    .withUsername("test")
                    .withPassword("test")
                    .withEnv("MYSQL_ROOT_HOST", "%")
                    // Allow more time for MySQL to initialize
                    .withStartupTimeout(java.time.Duration.ofMinutes(5));

            // Start the container once at JVM startup
            tempContainer.start();
            System.out.println("[TestContainer] MySQL container started successfully!");
            System.out.println("[TestContainer] JDBC URL: " + tempContainer.getJdbcUrl());
            
            // Register shutdown hook - use final reference for lambda
            final MySQLContainer<?> containerRef = tempContainer;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (containerRef != null && containerRef.isRunning()) {
                    System.out.println("[TestContainer] Stopping MySQL container...");
                    containerRef.stop();
                    System.out.println("[TestContainer] MySQL container stopped.");
                }
            }));
        } catch (Exception e) {
            System.err.println("[TestContainer] ERROR: Failed to start MySQL container!");
            e.printStackTrace();
            tempError = e;
            // Don't rethrow - allow tests that don't use Testcontainers to run
            // Tests that require the container will fail gracefully when trying to use it
        }
        
        SHARED_MYSQL_CONTAINER = tempContainer;
        INITIALIZATION_ERROR = tempError;
    }

    /**
     * Get the shared MySQL container instance.
     * Guaranteed to be started and ready for connections.
     * 
     * @throws IllegalStateException if the container failed to initialize
     */
    public static MySQLContainer<?> getSharedContainer() {
        if (SHARED_MYSQL_CONTAINER == null) {
            throw new IllegalStateException(
                "MySQL Testcontainer failed to initialize. " +
                "Ensure Docker Desktop is running and accessible. " +
                "Original error: " + (INITIALIZATION_ERROR != null ? INITIALIZATION_ERROR.getMessage() : "Unknown"),
                INITIALIZATION_ERROR
            );
        }
        return SHARED_MYSQL_CONTAINER;
    }
    
    /**
     * Check if the container is available and ready
     */
    public static boolean isContainerReady() {
        return SHARED_MYSQL_CONTAINER != null && SHARED_MYSQL_CONTAINER.isRunning();
    }
}
