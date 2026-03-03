package com.sistema.tramites.backend;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TramiteRepository extends JpaRepository<Tramite, Long> {
	Optional<Tramite> findByNumeroRadicadoIgnoreCase(String numeroRadicado);
	Optional<Tramite> findByCodigoVerificacionIgnoreCase(String codigoVerificacion);
	List<Tramite> findAllByFechaRadicacionBetweenAndConsecutivoVerificadorIsNotNull(LocalDateTime inicio, LocalDateTime fin);
}
