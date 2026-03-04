package com.sistema.tramites.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditoriaTramiteRepository extends JpaRepository<AuditoriaTramite, Long> {
	List<AuditoriaTramite> findAllByTramiteIdOrderByFechaIntegracionDesc(Long tramiteId);
	List<AuditoriaTramite> findTop200ByOrderByFechaIntegracionDesc();
	List<AuditoriaTramite> findTop200ByFechaIntegracionGreaterThanEqualOrderByFechaIntegracionDesc(LocalDateTime fechaDesde);
}
