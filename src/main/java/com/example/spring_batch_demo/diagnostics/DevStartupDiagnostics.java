package com.example.spring_batch_demo.diagnostics;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevStartupDiagnostics implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevStartupDiagnostics.class);

    private final JdbcTemplate jdbcTemplate;

    public DevStartupDiagnostics(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String user = jdbcTemplate.queryForObject("SELECT USER FROM dual", String.class);
            log.info("Oracle connected user={}", user);

            List<String> tables = jdbcTemplate.queryForList(
                    "SELECT table_name FROM user_tables WHERE table_name IN ('CUSTOMER')",
                    String.class
            );
            log.info("Oracle visible tables: {}", tables);
        } catch (Exception e) {
            log.error("Startup DB diagnostics failed (check Oracle connectivity/permissions).", e);
        }
    }
}

