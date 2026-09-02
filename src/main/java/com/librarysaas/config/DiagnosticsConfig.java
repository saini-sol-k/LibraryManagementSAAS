package com.librarysaas.config;

import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiagnosticsConfig {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsConfig.class);

    /**
     * Temporary diagnostic runner that prints JDBC metadata at startup.
     * Use this output to diagnose Flyway "Unsupported Database" issues.
     */
    @Bean
    public CommandLineRunner printJdbcMetadata(DataSource dataSource) {
        return args -> {
            try (var conn = dataSource.getConnection()) {
                DatabaseMetaData md = conn.getMetaData();
                // Print to stdout as well to ensure visibility during diagnostics
                System.out.println("JDBC driver: " + md.getDriverName() + " " + md.getDriverVersion());
                System.out.println("DB product: " + md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
                log.info("JDBC driver: {} {}", md.getDriverName(), md.getDriverVersion());
                log.info("DB product: {} {}", md.getDatabaseProductName(), md.getDatabaseProductVersion());
            } catch (Exception e) {
                log.warn("Unable to read JDBC metadata: {}", e.getMessage());
            }
        };
    }
}
