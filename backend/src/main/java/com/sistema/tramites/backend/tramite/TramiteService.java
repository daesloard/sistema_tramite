package com.sistema.tramites.backend.tramite;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.sistema.tramites.backend.util.TramiteUtils;
import java.util.*;

@Service
public class TramiteService {

    private final TramiteRepository tramiteRepository;
    private final TramiteUtils tramiteUtils;

    public TramiteService(TramiteRepository tramiteRepository, TramiteUtils tramiteUtils) {
        this.tramiteRepository = tramiteRepository;
        this.tramiteUtils = tramiteUtils;
    }

    public List<Map<String, Object>> listarResumen() {
        return tramiteRepository.findAllResumen()
                .stream()
                .map(this::construirResumenTramite)
                .toList();
    }

    public Optional<Tramite> obtenerPorId(Long id) {
        return tramiteRepository.findById(id);
    }

    public Tramite crear(Tramite tramite) {
        if (tramite.getNumeroRadicado() == null || tramite.getNumeroRadicado().isBlank()) {
            tramite.setNumeroRadicado(tramiteUtils.generarRadicado());
        }
        if (tramite.getFechaRadicacion() == null) {
            tramite.setFechaRadicacion(LocalDateTime.now());
        }
        if (tramite.getEstado() == null) {
            tramite.setEstado(EstadoTramite.RADICADO);
        }
        return tramiteRepository.save(tramite);
    }

    public Optional<Tramite> actualizar(Long id, Tramite entrada) {
        return tramiteRepository.findById(id)
                .map(actual -> {
                    actual.setNombreSolicitante(entrada.getNombreSolicitante());
                    actual.setTipoTramite(entrada.getTipoTramite());
                    actual.setDescripcion(entrada.getDescripcion());
                    actual.setEstado(entrada.getEstado() != null ? entrada.getEstado() : actual.getEstado());
                    return tramiteRepository.save(actual);
                });
    }

    public boolean eliminar(Long id) {
        if (!tramiteRepository.existsById(id)) return false;
        tramiteRepository.deleteById(id);
        return true;
    }

    public String generarCodigoVerificacion(String numeroRadicado) {
        return tramiteUtils.generarCodigoVerificacion(numeroRadicado);
    }

    private Map<String, Object> construirResumenTramite(TramiteResumenView tramite) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", tramite.getId());
        item.put("numeroRadicado", tramite.getNumeroRadicado());
        item.put("nombreSolicitante", tramite.getNombreSolicitante());
        item.put("tipoTramite", tramite.getTipoTramite());
        item.put("estado", tramite.getEstado() != null ? tramite.getEstado().name() : null);
        item.put("fechaRadicacion", tramite.getFechaRadicacion());
        item.put("fechaVencimiento", tramite.getFechaVencimiento());
        item.put("fechaVigencia", tramite.getFechaVigencia());
        item.put("fechaVerificacion", tramite.getFechaVerificacion());
        item.put("verificacionAprobada", tramite.getVerificacionAprobada());
        item.put("fechaFirmaAlcalde", tramite.getFechaFirmaAlcalde());
        item.put("tipoDocumento", tramite.getTipoDocumento());
        item.put("numeroDocumento", tramite.getNumeroDocumento());
        item.put("lugarExpedicionDocumento", tramite.getLugarExpedicionDocumento());
        item.put("direccionResidencia", tramite.getDireccionResidencia());
        item.put("barrioResidencia", tramite.getBarrioResidencia());
        item.put("telefono", tramite.getTelefono());
        item.put("correoElectronico", tramite.getCorreoElectronico());
        item.put("tipo_certificado", tramite.getTipo_certificado());
        item.put("observaciones", tramite.getObservaciones());
        item.put("consecutivoVerificador", tramite.getConsecutivoVerificador());
        item.put("ruta_certificado_final", tramite.getRuta_certificado_final());
        item.put("nombrePdfGenerado", tramite.getNombrePdfGenerado());
        item.put("motorPdfGenerado", tramite.getMotorPdfGenerado());
        return item;
    }
}
