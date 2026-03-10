package com.sistema.tramites.backend.auditoria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuditoriaSchemaMigration {

    private static final Logger logger = LoggerFactory.getLogger(AuditoriaSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public AuditoriaSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void removeInvalidForeignKeysForAuditoria() {
        try {
            List<String> foreignKeys = jdbcTemplate.query(
                    """
                    SELECT CONSTRAINT_NAME
                    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'auditoria_tramites'
                      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
                    """,
                    (rs, rowNum) -> rs.getString("CONSTRAINT_NAME")
            );

            for (String fkName : foreignKeys) {
                String safeFkName = fkName.replace("`", "");
                jdbcTemplate.execute("ALTER TABLE auditoria_tramites DROP FOREIGN KEY `" + safeFkName + "`");
                logger.info("✅ FK eliminada en auditoria_tramites: {}", safeFkName);
            }
        } catch (Exception ex) {
            logger.warn("⚠️ No fue posible ajustar llaves foráneas de auditoría: {}", ex.getMessage());
        }
    }
}
