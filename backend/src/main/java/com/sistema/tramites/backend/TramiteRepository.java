package com.sistema.tramites.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TramiteRepository extends JpaRepository<Tramite, Long> {
	Optional<Tramite> findByNumeroRadicadoIgnoreCase(String numeroRadicado);
	Optional<Tramite> findByCodigoVerificacionIgnoreCase(String codigoVerificacion);
	List<Tramite> findAllByFechaRadicacionBetweenAndConsecutivoVerificadorIsNotNull(LocalDateTime inicio, LocalDateTime fin);

	@Query("""
			select
				t.id as id,
				t.numeroRadicado as numeroRadicado,
				t.nombreSolicitante as nombreSolicitante,
				t.tipoTramite as tipoTramite,
				t.estado as estado,
				t.fechaRadicacion as fechaRadicacion,
				t.fechaVencimiento as fechaVencimiento,
				t.fechaVigencia as fechaVigencia,
				t.fechaVerificacion as fechaVerificacion,
				t.verificacionAprobada as verificacionAprobada,
				t.fechaFirmaAlcalde as fechaFirmaAlcalde,
				t.tipoDocumento as tipoDocumento,
				t.numeroDocumento as numeroDocumento,
				t.lugarExpedicionDocumento as lugarExpedicionDocumento,
				t.direccionResidencia as direccionResidencia,
				t.barrioResidencia as barrioResidencia,
				t.telefono as telefono,
				t.correoElectronico as correoElectronico,
				t.tipo_certificado as tipo_certificado,
				t.observaciones as observaciones,
				t.consecutivoVerificador as consecutivoVerificador,
				t.ruta_certificado_final as ruta_certificado_final,
				t.nombrePdfGenerado as nombrePdfGenerado,
				t.motorPdfGenerado as motorPdfGenerado
			from Tramite t
			order by t.fechaRadicacion asc
		""")
	List<TramiteResumenView> findAllResumen();
}
