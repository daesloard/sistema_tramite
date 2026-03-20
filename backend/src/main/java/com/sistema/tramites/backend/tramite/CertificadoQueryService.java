// ...existing code...
package com.sistema.tramites.backend.tramite;
import com.sistema.tramites.backend.documento.DocxtemplaterService;

import com.sistema.tramites.backend.auditoria.AuditoriaTramite;
import com.sistema.tramites.backend.auditoria.AuditoriaTramiteService;
import com.sistema.tramites.backend.documento.DocumentoGeneradoService;
import com.sistema.tramites.backend.documento.DriveStorageService;
import com.sistema.tramites.backend.tramite.dto.DocumentoDescargaDTO;
import com.sistema.tramites.backend.usuario.RolUsuario;
import com.sistema.tramites.backend.usuario.Usuario;
import com.sistema.tramites.backend.usuario.UsuarioRepository;
import com.sistema.tramites.backend.util.HashUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CertificadoQueryService {

    private static final String DRIVE_PREFIX = "drive:";
    private final TramiteRepository tramiteRepository;
    private final UsuarioRepository usuarioRepository;
    private final DriveStorageService driveStorageService;
    private final DocumentoGeneradoService documentoGeneradoService;
    private final DocxtemplaterService docxtemplaterService;
    private final AuditoriaTramiteService auditoriaTramiteService;

    public CertificadoQueryService(TramiteRepository tramiteRepository,
                                   UsuarioRepository usuarioRepository,
                                   DriveStorageService driveStorageService,
                                   DocumentoGeneradoService documentoGeneradoService,
                                   DocxtemplaterService docxtemplaterService,
                                   AuditoriaTramiteService auditoriaTramiteService) {
        this.tramiteRepository = tramiteRepository;
        this.usuarioRepository = usuarioRepository;
        this.driveStorageService = driveStorageService;
        this.documentoGeneradoService = documentoGeneradoService;
        this.docxtemplaterService = docxtemplaterService;
        this.auditoriaTramiteService = auditoriaTramiteService;
    }

@Transactional(readOnly = true)
    public Map<String, Object> verificarCertificado(String numeroRadicado, String factorTipo, String factorValor) {
        String criterio = numeroRadicado == null ? "" : numeroRadicado.trim();
        if (criterio.isBlank()) throw new IllegalArgumentException("Radicado o hash requerido");

        String tipo = factorTipo == null ? "" : factorTipo.trim().toUpperCase(Locale.ROOT);
        String valor = factorValor == null ? "" : factorValor.trim();
        if (tipo.isBlank() || valor.isBlank()) throw new IllegalArgumentException("Debes enviar el factor de validación (tipo y valor)");

        Optional<Tramite> optTramite = tramiteRepository.findByNumeroRadicadoIgnoreCase(criterio);
        if (optTramite.isEmpty()) optTramite = tramiteRepository.findByHashDocumentoGeneradoIgnoreCase(criterio);

        Tramite t = optTramite.orElseThrow(() -> new IllegalArgumentException("Radicado o hash no encontrado"));

        if (!validarFactorReconocimiento(t, tipo, valor)) {
            throw new SecurityException("El dato de validación no coincide con el titular de la solicitud");
        }

        String hashActual = HashUtils.sha256Hex(t.getContenidoPdfGenerado());
        boolean documentoIntegro = t.getContenidoPdfGenerado() != null && t.getContenidoPdfGenerado().length > 0
                && t.getHashDocumentoGenerado() != null && !t.getHashDocumentoGenerado().isBlank()
                && Objects.equals(t.getHashDocumentoGenerado(), hashActual);

        Map<String, Object> r = new HashMap<>();
        r.put("numeroRadicado", t.getNumeroRadicado());
        r.put("codigoVerificacion", t.getCodigoVerificacion());
        r.put("estado", t.getEstado().toString());
        r.put("nombreSolicitante", t.getNombreSolicitante());
        r.put("numeroDocumento", t.getNumeroDocumento());
        r.put("lugarExpedicionDocumento", t.getLugarExpedicionDocumento());
        r.put("direccionResidencia", t.getDireccionResidencia());
        r.put("barrioResidencia", t.getBarrioResidencia());
        r.put("fechaRadicacion", t.getFechaRadicacion());
        r.put("documentoIntegro", documentoIntegro);
        r.put("hashRegistrado", t.getHashDocumentoGenerado());
        r.put("hashActual", hashActual);
        r.put("dobleValidacion", true);
        r.put("factorValidado", tipo);

        if (t.getEstado() == EstadoTramite.FINALIZADO) {
            LocalDate hoy = LocalDate.now();
            boolean vigente = hoy.isBefore(t.getFechaVigencia()) || hoy.isEqual(t.getFechaVigencia());
            r.put("certificadoEmitido", true);
            r.put("vigente", vigente);
            r.put("fechaVigencia", t.getFechaVigencia());
            r.put("fechaFirmaAlcalde", t.getFechaFirmaAlcalde());
            r.put("tiposCertificado", t.getTipo_certificado());
        } else {
            r.put("certificadoEmitido", false);
            r.put("vigente", null);
            r.put("fechaVigencia", null);
            r.put("fechaFirmaAlcalde", null);
            r.put("mensaje", "El certificado aún no ha sido emitido. Estado actual: " + t.getEstado());
        }

        if (t.getEstado() == EstadoTramite.RECHAZADO && t.getObservaciones() != null) {
            r.put("observaciones", t.getObservaciones());
        }
        return r;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> consultarSolicitudesResueltas(String numeroDocumento) {
        String documento = numeroDocumento == null ? "" : numeroDocumento.trim();
        if (documento.isBlank()) throw new IllegalArgumentException("Número de documento requerido");
        if (!documento.matches("\\d+")) throw new IllegalArgumentException("El numero de documento solo debe contener digitos");

        return tramiteRepository.findAll().stream()
                .filter(t -> t.getNumeroDocumento() != null && documento.equalsIgnoreCase(t.getNumeroDocumento().trim()))
                .filter(t -> t.getEstado() == EstadoTramite.FINALIZADO || t.getEstado() == EstadoTramite.RECHAZADO)
                .sorted((a, b) -> b.getFechaRadicacion().compareTo(a.getFechaRadicacion()))
                .map(t -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", t.getId());
                    item.put("numeroRadicado", t.getNumeroRadicado());
                    item.put("estado", t.getEstado() != null ? t.getEstado().name() : null);
                    item.put("fechaRadicacion", t.getFechaRadicacion());
                    item.put("fechaFirmaAlcalde", t.getFechaFirmaAlcalde());
                    item.put("fechaVigencia", t.getFechaVigencia());
                    item.put("tipoCertificado", t.getTipo_certificado());
                    item.put("observaciones", t.getObservaciones());
                    item.put("certificadoDisponible", (t.getContenidoPdfGenerado() != null && t.getContenidoPdfGenerado().length > 0)
                            || extraerDriveFileId(t.getRuta_certificado_final()) != null);
                    return item;
                }).toList();
    }

    @Transactional
    public DocumentoDescargaDTO descargarDocumentoGenerado(Long id, String accion, String usernameHeader, String adminUsernameHeader) {
        Tramite tramite = tramiteRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado"));
        byte[] contenido = tramite.getContenidoPdfGenerado();

        if ((contenido == null || contenido.length == 0) && driveStorageService.isEnabled()) {
            String driveFileId = extraerDriveFileId(tramite.getRuta_certificado_final());
            if (driveFileId != null) {
                try {
                    contenido = driveStorageService.downloadFile(driveFileId);
                } catch (Exception e) {
                    throw new RuntimeException("Error al descargar de Drive", e);
                }
            }
        }

        if (contenido == null || contenido.length == 0) {
            throw new IllegalStateException("No existe documento generado para este trámite");
        }

        Long usuarioId = resolverUsuarioIdPorHeaders(usernameHeader, adminUsernameHeader);
        String accionAuditoria = resolverAccionDocumentoGenerado(accion);
        String almacenamiento = extraerDriveFileId(tramite.getRuta_certificado_final()) != null ? "DRIVE" : "BD";
        auditoriaTramiteService.registrarEvento(
                tramite.getId(), usuarioId, accionAuditoria,
                "Acceso a certificado generado en " + almacenamiento,
                tramite.getEstado(), tramite.getEstado()
        );

        String nombreArchivo = (tramite.getNombrePdfGenerado() != null && !tramite.getNombrePdfGenerado().isBlank())
                ? tramite.getNombrePdfGenerado() : "documento_generado_" + tramite.getNumeroRadicado() + ".pdf";

        return new DocumentoDescargaDTO(nombreArchivo, contenido);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenerAuditoriaTramite(Long id, String adminUsername) {
        if (adminUsername == null || adminUsername.isBlank()) throw new SecurityException("Debes autenticarte como administrador");
        usuarioRepository.findByUsernameAndRolAndActivoTrue(adminUsername.trim(), RolUsuario.ADMINISTRADOR)
                .orElseThrow(() -> new SecurityException("Usuario administrador no válido o inactivo"));

        Tramite tramite = tramiteRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado"));
        List<AuditoriaTramite> eventos = auditoriaTramiteService.listarPorTramite(id);

        Set<Long> usuarioIds = eventos.stream().map(AuditoriaTramite::getUsuarioId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Usuario> usuariosPorId = usuarioIds.isEmpty() ? Map.of() : usuarioRepository.findAllById(usuarioIds).stream().collect(Collectors.toMap(Usuario::getId, u -> u));

        List<Map<String, Object>> eventosRespuesta = eventos.stream().map(evento -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", evento.getId());
            item.put("accion", evento.getAccion());
            item.put("descripcion", evento.getDescripcion());
            item.put("estadoAnterior", evento.getEstadoAnterior());
            item.put("estadoNuevo", evento.getEstadoNuevo());
            item.put("fechaIntegracion", evento.getFechaIntegracion());
            item.put("usuarioId", evento.getUsuarioId());
            Usuario usuario = evento.getUsuarioId() != null ? usuariosPorId.get(evento.getUsuarioId()) : null;
            item.put("username", usuario != null ? usuario.getUsername() : null);
            item.put("nombreCompleto", usuario != null ? usuario.getNombreCompleto() : null);
            item.put("rol", usuario != null && usuario.getRol() != null ? usuario.getRol().name() : null);
            return item;
        }).toList();

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("tramiteId", tramite.getId());
        respuesta.put("numeroRadicado", tramite.getNumeroRadicado());
        respuesta.put("totalEventos", eventosRespuesta.size());
        respuesta.put("eventos", eventosRespuesta);
        return respuesta;
    }

    @Transactional
    public Map<String, Object> vistaPreviaDocumento(Long id, boolean includePdf, String usernameHeader, String adminUsernameHeader) {
        Tramite tramite = tramiteRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Trámite no encontrado"));
        boolean aprobado = esDecisionAprobada(tramite);
        String contenido = ""; // Eliminar lógica antigua
        String html = ""; // Eliminar lógica antigua

        byte[] pdfVistaPrevia = null;
        String errorPdf = null;
        if (includePdf) {
            try {
                String plantilla = documentoGeneradoService.obtenerNombrePlantilla(tramite, aprobado);
                byte[] docxProcesado = docxtemplaterService.processTemplate(plantilla, tramite, aprobado, null);
                pdfVistaPrevia = documentoGeneradoService.convertirDocxAGotenberg(docxProcesado);
            } catch (Exception ex) { errorPdf = ex.getMessage(); }
        }

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("tramiteId", tramite.getId());
        respuesta.put("numeroRadicado", tramite.getNumeroRadicado());
        respuesta.put("estado", tramite.getEstado() != null ? tramite.getEstado().name() : null);
        String templateName;
        if (!aprobado || tramite.getVerificacionAprobada() != null && !tramite.getVerificacionAprobada()) {
            templateName = "RESPUESTA NEGATIVA.docx";
        } else {
            String tipo = (tramite.getTipo_certificado() != null ? tramite.getTipo_certificado().trim() : "");
            switch (tipo.toUpperCase()) {
                case "SISBEN":
                case "CERTIFICADO_SISBEN":
                    templateName = "CARTA RESIDENCIA SISBEN.docx";
                    break;
                case "ELECTORAL":
                case "CERTIFICADO_ELECTORAL":
                case "REGISTRADURIA NACIONAL":
                case "REGISTRADURIA":
                    templateName = "CARTA RESIDENCIA REGISTRADURIA NACIONAL.docx";
                    break;
                case "JAC":
                case "JUNTA DE ACCION":
                case "CERTIFICADO_RESIDENCIA":
                default:
                    templateName = "CARTA RESIDENCIA JUNTA DE ACCION.docx";
                    break;
            }
        }
        respuesta.put("plantilla", templateName);

        respuesta.put("contenido", contenido);
        respuesta.put("html", html);
        respuesta.put("pdfBase64", pdfVistaPrevia != null && pdfVistaPrevia.length > 0 ? Base64.getEncoder().encodeToString(pdfVistaPrevia) : null);
        respuesta.put("pdfDisponible", pdfVistaPrevia != null && pdfVistaPrevia.length > 0);
        if (errorPdf != null && !errorPdf.isBlank()) respuesta.put("pdfError", errorPdf);

        Long usuarioId = resolverUsuarioIdPorHeaders(usernameHeader, adminUsernameHeader);
        auditoriaTramiteService.registrarEvento(
                tramite.getId(), usuarioId, includePdf ? "VISTA_PREVIA_DOCUMENTO_CON_PDF" : "VISTA_PREVIA_DOCUMENTO",
                "Consulta de vista previa", tramite.getEstado(), tramite.getEstado()
        );
        return respuesta;
    }

    private boolean validarFactorReconocimiento(Tramite tramite, String tipo, String valorIngresado) {
        if (tramite == null) return false;
        if ("PRIMER_NOMBRE".equals(tipo)) {
            String primero = "";
            if (tramite.getNombreSolicitante() != null) {
                String nor = tramite.getNombreSolicitante().trim().replaceAll("\\s+", " ");
                primero = nor.indexOf(' ') > 0 ? nor.substring(0, nor.indexOf(' ')) : nor;
            }
            String v1 = normalizarTextoComparacion(valorIngresado);
            String v2 = normalizarTextoComparacion(primero);
            return !v1.isBlank() && v1.equals(v2);
        }
        if ("ULTIMOS_3_DOCUMENTO".equals(tipo)) {
            String d = tramite.getNumeroDocumento() == null ? "" : tramite.getNumeroDocumento().replaceAll("\\D", "");
            String v = valorIngresado.replaceAll("\\D", "");
            return d.length() >= 3 && v.length() == 3 && d.substring(d.length() - 3).equals(v);
        }
        return false;
    }

    private String normalizarTextoComparacion(String valor) {
        return valor == null ? "" : Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toUpperCase(Locale.ROOT);
    }

    private String extraerDriveFileId(String ruta) {
        return (ruta != null && ruta.trim().startsWith(DRIVE_PREFIX)) ? ruta.trim().substring(DRIVE_PREFIX.length()) : null;
    }

    private Long resolverUsuarioIdPorHeaders(String usernameHeader, String adminUsernameHeader) {
        String u = (usernameHeader != null && !usernameHeader.isBlank()) ? usernameHeader.trim() :
                (adminUsernameHeader != null && !adminUsernameHeader.isBlank() ? adminUsernameHeader.trim() : null);
        return u != null ? usuarioRepository.findByUsername(u).map(Usuario::getId).orElse(null) : null;
    }

    private String resolverAccionDocumentoGenerado(String accion) {
        String a = accion == null ? "" : accion.trim().toLowerCase(Locale.ROOT);
        if (a.equals("ver") || a.equals("visualizar") || a.equals("open")) return "CERTIFICADO_GENERADO_VISUALIZADO";
        if (a.equals("descargar") || a.equals("download")) return "CERTIFICADO_GENERADO_DESCARGADO";
        return "CERTIFICADO_GENERADO_ACCESO";
    }

    private boolean esDecisionAprobada(Tramite tramite) {
        if (tramite == null) return true;
        return tramite.getVerificacionAprobada() != null ? tramite.getVerificacionAprobada() : tramite.getEstado() != EstadoTramite.RECHAZADO;
    }
}
