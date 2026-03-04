package com.sistema.tramites.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UsuarioEmailSchemaMigration {

    private static final Logger logger = LoggerFactory.getLogger(UsuarioEmailSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public UsuarioEmailSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void removeUniqueConstraintFromUsuarioEmail() {
        try {
            List<String> uniqueEmailIndexes = jdbcTemplate.query(
                    """
                    SELECT INDEX_NAME
                    FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'usuarios'
                      AND NON_UNIQUE = 0
                    GROUP BY INDEX_NAME
                    HAVING SUM(CASE WHEN COLUMN_NAME = 'email' THEN 1 ELSE 0 END) = 1
                       AND COUNT(*) = 1
                       AND INDEX_NAME <> 'PRIMARY'
                    """,
                    (rs, rowNum) -> rs.getString("INDEX_NAME")
            );

            for (String indexName : uniqueEmailIndexes) {
                String safeIndexName = indexName.replace("`", "");
                jdbcTemplate.execute("ALTER TABLE usuarios DROP INDEX `" + safeIndexName + "`");
                logger.info("✅ Índice único removido de usuarios.email: {}", safeIndexName);
            }

            Integer emailIndexCount = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'usuarios'
                      AND INDEX_NAME = 'idx_email'
                    """,
                    Integer.class
            );

            if (emailIndexCount == null || emailIndexCount == 0) {
                jdbcTemplate.execute("CREATE INDEX idx_email ON usuarios(email)");
                logger.info("✅ Índice no único creado para usuarios.email (idx_email)");
            }
        } catch (Exception ex) {
            logger.warn("⚠️ No fue posible ajustar índice de usuarios.email automáticamente: {}", ex.getMessage());
        }
    }
}
