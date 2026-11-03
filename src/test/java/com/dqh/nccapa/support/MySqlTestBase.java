package com.dqh.nccapa.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

/**
 * Base cho test chạm DB: dùng Testcontainers MySQL 8 (giống production).
 * YÊU CẦU: Docker đang chạy trên máy test. Nếu không có Docker, các test kế thừa base này sẽ bị skip ở tầng hạ tầng.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class MySqlTestBase {

    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("nc_capa")
            .withUsername("nc_capa")
            .withPassword("nc_capa");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.hikari.transaction-isolation", () -> "TRANSACTION_READ_COMMITTED");
    }

    @Autowired
    private JdbcTemplate baseJdbc;

    /**
     * Làm sạch DB trước mỗi test — TRUNCATE mọi bảng nghiệp vụ (trừ flyway_schema_history).
     * Cần thiết vì các test chia sẻ chung một container: CAPA đang Monitoring sót lại từ
     * test trước có thể kích hoạt TH2 (fail sớm) nhầm ở test sau. Chạy TRƯỚC @BeforeEach
     * của lớp con (JUnit 5 gọi superclass trước), nên seed của lớp con vẫn đứng sau.
     */
    @BeforeEach
    void resetDatabase() {
        List<String> tables = baseJdbc.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'",
                String.class);
        baseJdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String t : tables) {
            baseJdbc.execute("TRUNCATE TABLE `" + t + "`");
        }
        baseJdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }
}
