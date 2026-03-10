package com.sistema.tramites.backend.controladores;



import com.sistema.tramites.backend.tramite.*;
import com.sistema.tramites.backend.tramite.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/tramites")
public class TramiteController {
    private final TramiteService tramiteService;
    private final RadicacionService radicacionService;
    private final VerificacionService verificacionService;
    private final FirmaService firmaService;
    private final CertificadoQueryService certificadoQueryService;
    private final ExportService exportService;
    public TramiteController(TramiteService tramiteService,
                             RadicacionService radicacionService,
                             VerificacionService verificacionService,
                             FirmaService firmaService,
                             CertificadoQueryService certificadoQueryService,
                             ExportService exportService) {
        this.tramiteService = tramiteService;
        this.radicacionService = radicacionService;
        this.verificacionService = verificacionService;
        this.firmaService = firmaService;
        this.certificadoQueryService = certificadoQueryService;
        this.exportService = exportService;
    }
    @GetMapping
    public List<Map<String, Object>> listar() {
        return tramiteService.listarResumen();
    }
    @GetMapping("/{id}")
    public ResponseEntity<Tramite> obtener(@PathVariable Long id) {
        return tramiteService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    @PostMapping
    public ResponseEntity<Tramite> crear(@Valid @RequestBody Tramite tramite) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tramiteService.crear(tramite));
    }
    @PutMapping("/{id}")
    public ResponseEntity<Tramite> actualizar(@PathVariable Long id, @Valid @RequestBody Tramite entrada) {
        return tramiteService.actualizar(id, entrada)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (tramiteService.eliminar(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
    @PostMapping("/solicitud-residencia")
    public ResponseEntity<?> radicacionCertificadoResidencia(@Valid @RequestBody SolicitudCertificadoResidenciaDTO solicitud) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(radicacionService.radicacionCertificadoResidencia(solicitud));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error en la radicación: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error en la radicación: " + e.getMessage());
        }
    }
    @PostMapping("/{id}/verificacion")
    public ResponseEntity<?> verificarSolicitud(@PathVariable Long id, @Valid @RequestBody VerificacionSolicitudDTO verificacion) {
        try {
            return ResponseEntity.ok(verificacionService.verificarSolicitud(id, verificacion));
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("No encontrado") || e.getMessage().contains("no encontrado")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error en verificación: " + e.getMessage());
        }
    }
    @PostMapping("/{id}/notificar-verificador")
    public ResponseEntity<?> notificarVerificadorDesdeAdmin(@PathVariable Long id, @RequestBody(required = false) NotificacionVerificadorAdminDTO request, @RequestHeader(value = "X-Admin-Username", required = false) String adminUsername) {
        try {
            return ResponseEntity.ok(verificacionService.notificarVerificadorDesdeAdmin(id, request, adminUsername));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("❌ " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("❌ " + e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("❌ " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("❌ Error al notificar verificador: " + e.getMessage());
        }
    }
    @PostMapping("/{id}/firma-alcalde")
    public ResponseEntity<?> firmarDocumento(@PathVariable Long id, @Valid @RequestBody FirmaAlcaldeDTO firma) {
        try {
            Map<String, Object> r = firmaService.firmarDocumento(id, firma);
            if (r.containsKey("duracionReintentoPostFirmaMs")) {
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(r);
            }
            return ResponseEntity.ok(r);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error al firmar: " + e.getMessage());
        }
    }
    @GetMapping("/verificacion/{numeroRadicado}")
    public ResponseEntity<?> verificarCertificado(@PathVariable String numeroRadicado, @RequestParam(value = "factorTipo", required = false) String factorTipo, @RequestParam(value = "factorValor", required = false) String factorValor) {
        try {
            return ResponseEntity.ok(certificadoQueryService.verificarCertificado(numeroRadicado, factorTipo, factorValor));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("no encontrado")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error en verificación: " + e.getMessage());
        }
    }
    @GetMapping("/verificacion/resueltas/{numeroDocumento}")
    public ResponseEntity<?> consultarSolicitudesResueltas(@PathVariable String numeroDocumento) {
        try {
            return ResponseEntity.ok(certificadoQueryService.consultarSolicitudesResueltas(numeroDocumento));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error consultando solicitudes resueltas: " + e.getMessage());
        }
    }
    @GetMapping("/{id}/documento-generado")
    public ResponseEntity<?> descargarDocumentoGenerado(@PathVariable Long id, @RequestParam(value = "accion", required = false) String accion, @RequestHeader(value = "X-Username", required = false) String usernameHeader, @RequestHeader(value = "X-Admin-Username", required = false) String adminUsernameHeader) {
        try {
            DocumentoDescargaDTO dto = certificadoQueryService.descargarDocumentoGenerado(id, accion, usernameHeader, adminUsernameHeader);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dto.getNombreArchivo() + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                    .body(dto.getContenido());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al descargar documento generado: " + e.getMessage());
        }
    }
    @GetMapping("/{id}/auditoria")
    public ResponseEntity<?> obtenerAuditoriaTramite(@PathVariable Long id, @RequestHeader(value = "X-Admin-Username", required = false) String adminUsername) {
        try {
            return ResponseEntity.ok(certificadoQueryService.obtenerAuditoriaTramite(id, adminUsername));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("❌ " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("❌ " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("❌ Error consultando auditoría: " + e.getMessage());
        }
    }
    @GetMapping("/{id}/vista-previa-documento")
    public ResponseEntity<?> vistaPreviaDocumento(@PathVariable Long id, @RequestParam(value = "includePdf", defaultValue = "false") boolean includePdf, @RequestHeader(value = "X-Username", required = false) String usernameHeader, @RequestHeader(value = "X-Admin-Username", required = false) String adminUsernameHeader) {
        try {
            return ResponseEntity.ok(certificadoQueryService.vistaPreviaDocumento(id, includePdf, usernameHeader, adminUsernameHeader));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al generar vista previa: " + e.getMessage());
        }
    }
    @GetMapping("/reporte/consolidado-verificaciones")
    public ResponseEntity<?> descargarConsolidadoVerificaciones() {
        try {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"consolidado_verificaciones.xlsx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(exportService.descargarConsolidadoVerificaciones());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al generar el consolidado Excel: " + e.getMessage());
        }
    }
}
