package com.sistema.tramites.backend.util;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import com.sistema.tramites.backend.tramite.Tramite;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private static final String EMAIL_SISTEMAS_TEMPORAL = "sistemas@cabuyaro-meta.gov.co";
    private static final long MAX_ADJUNTO_RESULTADO_BYTES = 8L * 1024L * 1024L;
    
    @Value("${app.mail.from:sistemas@cabuyaro-meta.gov.co}")
    private String emailRemitente;

    @Value("${app.mail.attachments.on-radicacion:false}")
    private boolean adjuntarSoportesEnRadicacion;

    @Value("${app.mail.max-attempts:3}")
    private int maxIntentosEnvio;

    @Value("${app.mail.retry-delay-ms:700}")
    private long esperaReintentoMs;

    @Value("${app.mail.send-rejected-result:false}")
    private boolean enviarResultadoRechazado;

    @Autowired
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Envía email de confirmación de radicación
     */
    public void enviarConfirmacionRadicacion(String correoDestino, String nombre, 
                                             String numeroRadicado, LocalDate fechaVencimiento) {
        enviarConfirmacionRadicacion(correoDestino, nombre, numeroRadicado, fechaVencimiento, null);
    }

    @Async("mailTaskExecutor")
    public void enviarConfirmacionRadicacion(String correoDestino, String nombre,
                                             String numeroRadicado, LocalDate fechaVencimiento,
                                             Tramite tramite) {
        try {
            String destino = normalizarCorreo(correoDestino);
            if (destino.isBlank()) {
                logger.warn("⚠️ Se omitió envío de confirmación: correo destino vacío. Radicado={}", numeroRadicado);
                return;
            }

            logger.info("📧 Enviando email de confirmación a: {}", destino);
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper mensaje = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            mensaje.setTo(destino);
            mensaje.setFrom(Objects.requireNonNull(obtenerRemitente()));
            mensaje.setSubject("Confirmación de Radicación - Solicitud de Certificado de Residencia");
            
            String fechaFormato = fechaVencimiento.format(DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy"));
            
            String contenido = "Estimado/a " + nombre + ",\n\n" +
                    "Gracias por usar nuestro portal. Queremos confirmarte que hemos recibido tu petición " +
                    "y actualmente estamos procesando el documento.\n\n" +
                    "Estará listo en un término de máximo 10 días hábiles y te lo enviaremos directamente " +
                    "a tu correo electrónico.\n\n" +
                    "Este es tu número de radicado: " + numeroRadicado + "\n" +
                    "Radicado el día: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy")) + "\n" +
                    "Fecha estimada de entrega: " + fechaFormato + "\n\n" +
                    "Si tienes alguna pregunta adicional, no dudes en contactarnos.\n\n" +
                    "Saludos cordiales,\n" +
                    "Ventanilla Virtual";

                    mensaje.setText(contenido);

                    if (adjuntarSoportesEnRadicacion && tramite != null) {
                    adjuntarSiExiste(mensaje,
                        tramite.getContenidoDocumentoSolicitud(),
                        tramite.getNombreArchivoSolicitud(),
                        tramite.getTipoContenidoSolicitud(),
                        "documento_solicitud.pdf");

                    adjuntarSiExiste(mensaje,
                        tramite.getContenidoDocumentoIdentidad(),
                        tramite.getNombreArchivoIdentidad(),
                        tramite.getTipoContenidoIdentidad(),
                        "documento_identidad.pdf");

                    boolean certificadoAdjunto = adjuntarCertificadoSoporte(mensaje, tramite);
                    if (!certificadoAdjunto) {
                        logger.warn("⚠️ No se encontró certificado para adjuntar en correo de radicación. Radicado={}", numeroRadicado);
                    }
                    }

            boolean enviado = enviarConReintento(() -> mailSender.send(mimeMessage), "confirmación", destino, numeroRadicado);
            if (enviado) {
                logger.info("✅ Email de confirmación enviado exitosamente a: {}", destino);
            }
            
        } catch (Exception e) {
            logger.error("❌ Error al enviar email de confirmación: {}", e.getMessage(), e);
        }
    }

    /**
     * Envía email al verificador notificando nueva solicitud
     */
    @Async("mailTaskExecutor")
    public void enviarNotificacionVerificador(String correoVerificador, String numeroRadicado, 
                                              String nombreSolicitante) {
        try {
            String destino = normalizarCorreo(correoVerificador);
            if (destino.isBlank()) {
                logger.warn("⚠️ Se omitió notificación al verificador: correo vacío. Radicado={}", numeroRadicado);
                return;
            }

            logger.info("📧 Enviando notificación de verificación a: {}", destino);
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setTo(destino);
            mensaje.setFrom(Objects.requireNonNull(obtenerRemitente()));
            mensaje.setSubject("Nueva Solicitud de Verificación - " + numeroRadicado);
            
            String contenido = "Nueva solicitud de verificación:\n\n" +
                    "Número de radicado: " + numeroRadicado + "\n" +
                    "Nombre del solicitante: " + nombreSolicitante + "\n" +
                    "Tipo de trámite: Certificado de Residencia\n\n" +
                    "Por favor, inicia sesión en el portal para revisar la documentación " +
                    "y completar el checklist de verificación.\n\n" +
                    "Ventanilla Virtual";
            
            mensaje.setText(contenido);
            boolean enviado = enviarConReintento(() -> mailSender.send(mensaje), "notificación verificador", destino, numeroRadicado);
            if (enviado) {
                logger.info("✅ Notificación de verificación enviada a: {}", destino);
            }
            
        } catch (Exception e) {
            logger.error("❌ Error al enviar email de verificador: {}", e.getMessage(), e);
        }
    }

    @Async("mailTaskExecutor")
    public void enviarNotificacionAdminRevisionDocumentos(
            String[] correosAdmin,
            String numeroRadicado,
            String nombreSolicitante,
            String tipoCertificado,
            String verificadorUsername,
            int documentosCargados,
            int documentosRequeridos,
            String detalleFaltantes,
            String mensajeAdicional) {
        try {
            if (correosAdmin == null || correosAdmin.length == 0) {
                logger.warn("⚠️ No hay correos de administrador para notificación de revisión documental");
                return;
            }

            logger.info("📧 Enviando notificación de revisión documental a admins: {}", String.join(",", correosAdmin));
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setTo(correosAdmin);
            mensaje.setFrom(Objects.requireNonNull(obtenerRemitente()));
            mensaje.setSubject("Solicitud de revisión documental - " + numeroRadicado);

            String contenido = "El verificador ha solicitado apoyo de administración para revisar documentos cargados.\n\n"
                    + "Radicado: " + numeroRadicado + "\n"
                    + "Solicitante: " + nombreSolicitante + "\n"
                    + "Tipo de certificado: " + (tipoCertificado == null || tipoCertificado.isBlank() ? "NO ESPECIFICADO" : tipoCertificado) + "\n"
                    + "Verificador: " + (verificadorUsername == null || verificadorUsername.isBlank() ? "verificador" : verificadorUsername) + "\n"
                    + "Documentos cargados: " + documentosCargados + " de " + documentosRequeridos + "\n"
                    + "Faltantes detectados: " + (detalleFaltantes == null || detalleFaltantes.isBlank() ? "NINGUNO" : detalleFaltantes) + "\n";

            if (mensajeAdicional != null && !mensajeAdicional.isBlank()) {
                contenido += "\nMensaje del verificador: " + mensajeAdicional + "\n";
            }

            contenido += "\nPor favor ingresa al sistema y valida el estado de la documentación.\n\n"
                    + "Ventanilla Virtual";

            mensaje.setText(contenido);
            boolean enviado = enviarConReintento(() -> mailSender.send(mensaje), "notificación administradores", String.join(",", correosAdmin), numeroRadicado);
            if (enviado) {
                logger.info("✅ Notificación de revisión documental enviada a administradores");
            }
        } catch (Exception e) {
            logger.error("❌ Error al enviar notificación a administradores: {}", e.getMessage(), e);
        }
    }

    /**
     * Envía email con documento final (aprobado o rechazado)
     */
    public void enviarDocumentoFinal(String correoDestino, String nombre, String numeroRadicado, 
                                     boolean aprobado, String observaciones) {
        enviarDocumentoFinal(correoDestino, nombre, numeroRadicado, aprobado, observaciones, null, null);
    }

    @Async("mailTaskExecutor")
    public void enviarDocumentoFinal(String correoDestino, String nombre, String numeroRadicado,
                                     boolean aprobado, String observaciones,
                                     byte[] adjuntoPdf, String nombreAdjunto) {
        try {
            if (!aprobado && !enviarResultadoRechazado) {
                logger.info("⏸️ Envío de correo de rechazo deshabilitado por configuración. Radicado={}", numeroRadicado);
                return;
            }

            String destino = normalizarCorreo(correoDestino);
            if (destino.isBlank()) {
                logger.warn("⚠️ Se omitió envío de resultado: correo destino vacío. Radicado={}", numeroRadicado);
                return;
            }

            String estado = aprobado ? "APROBADO" : "RECHAZADO";
            logger.info("📧 Enviando email de resultado ({}) a: {}", estado, destino);
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper mensaje = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            mensaje.setTo(destino);
            mensaje.setFrom(Objects.requireNonNull(obtenerRemitente()));
            
            String asunto = "Resultado de tu Solicitud - " + estado + " - " + numeroRadicado;
            
            mensaje.setSubject(asunto);
            
            String contenido = "Estimado/a " + nombre + ",\n\n" +
                    "Hemos finalizado la revisión de tu solicitud con radicado: " + numeroRadicado + "\n\n" +
                    "Estado: " + estado + "\n";
            
            if (observaciones != null && !observaciones.isEmpty()) {
                contenido += "Observaciones: " + observaciones + "\n\n";
            }
            
            contenido += "Para mayor información, por favor consulta tu radicado en nuestro portal " +
                    "o contáctanos directamente.\n\n" +
                    "Saludos cordiales,\n" +
                    "Ventanilla Virtual";
            
            mensaje.setText(contenido);

            if (adjuntoPdf != null && adjuntoPdf.length > 0 && adjuntoPdf.length <= MAX_ADJUNTO_RESULTADO_BYTES) {
                String nombreArchivoAdjunto = (nombreAdjunto != null && !nombreAdjunto.isBlank())
                        ? nombreAdjunto
                        : "respuesta_solicitud_" + numeroRadicado + ".pdf";
                mensaje.addAttachment(nombreArchivoAdjunto, new ByteArrayResource(adjuntoPdf), "application/pdf");
            } else if (adjuntoPdf != null && adjuntoPdf.length > MAX_ADJUNTO_RESULTADO_BYTES) {
                logger.warn("⚠️ PDF final no adjunto por tamaño ({}) para radicado={}", adjuntoPdf.length, numeroRadicado);
            }

            boolean enviado = enviarConReintento(() -> mailSender.send(mimeMessage), "resultado " + estado, destino, numeroRadicado);
            if (enviado) {
                logger.info("✅ Email de resultado ({}) enviado a: {}", estado, destino);
            }
            
        } catch (Exception e) {
            logger.error("❌ Error al enviar email de resultado: {}", e.getMessage(), e);
        }
    }

    private boolean enviarConReintento(Runnable envio, String tipo, String destino, String radicado) {
        int intentos = Math.max(1, maxIntentosEnvio);
        for (int intento = 1; intento <= intentos; intento++) {
            try {
                envio.run();
                return true;
            } catch (Exception ex) {
                logger.error("❌ Fallo envío {} intento {}/{} destino={} radicado={} causa={} mensaje={}",
                        tipo,
                        intento,
                        intentos,
                        destino,
                        radicado,
                        ex.getClass().getSimpleName(),
                        ex.getMessage(),
                        ex);

                if (intento < intentos) {
                    try {
                        Thread.sleep(Math.max(100, esperaReintentoMs));
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        return false;
    }

    private String obtenerRemitente() {
        String remitente = emailRemitente == null ? "" : emailRemitente.trim();
        return remitente.isBlank() ? EMAIL_SISTEMAS_TEMPORAL : remitente;
    }

    private String normalizarCorreo(String correo) {
        if (correo == null) {
            return "";
        }
        return correo.trim().toLowerCase();
    }

    private void adjuntarSiExiste(MimeMessageHelper mensaje,
                                  byte[] contenido,
                                  String nombreArchivo,
                                  String tipoContenido,
                                  String nombrePorDefecto) throws Exception {
        if (contenido == null || contenido.length == 0) {
            return;
        }

        String nombreFinal = (nombreArchivo != null && !nombreArchivo.isBlank())
                ? nombreArchivo
                : nombrePorDefecto;

        String tipoFinal = (tipoContenido != null && !tipoContenido.isBlank())
                ? tipoContenido
                : "application/octet-stream";

            mensaje.addAttachment(Objects.requireNonNull(nombreFinal), new ByteArrayResource(contenido), Objects.requireNonNull(tipoFinal));
    }

    private boolean adjuntarCertificadoSoporte(MimeMessageHelper mensaje, Tramite tramite) throws Exception {
        if (tramite == null) {
            return false;
        }

        String tipo = tramite.getTipo_certificado() == null ? "" : tramite.getTipo_certificado().trim().toUpperCase();

        if ("SISBEN".equals(tipo) && tieneContenido(tramite.getContenidoCertificadoSisben())) {
            adjuntarSiExiste(mensaje,
                    tramite.getContenidoCertificadoSisben(),
                    tramite.getNombreArchivoSisben(),
                    tramite.getTipoContenidoSisben(),
                    "certificado_sisben.pdf");
            return true;
        }

        if ("ELECTORAL".equals(tipo) && tieneContenido(tramite.getContenidoCertificadoElectoral())) {
            adjuntarSiExiste(mensaje,
                    tramite.getContenidoCertificadoElectoral(),
                    tramite.getNombreArchivoElectoral(),
                    tramite.getTipoContenidoElectoral(),
                    "certificado_electoral.pdf");
            return true;
        }

        if ("JAC".equals(tipo) && tieneContenido(tramite.getContenidoDocumentoResidencia())) {
            adjuntarSiExiste(mensaje,
                    tramite.getContenidoDocumentoResidencia(),
                    tramite.getNombreArchivoResidencia(),
                    tramite.getTipoContenidoResidencia(),
                    "certificado_residencia.pdf");
            return true;
        }

        if (tieneContenido(tramite.getContenidoDocumentoResidencia())) {
            adjuntarSiExiste(mensaje,
                    tramite.getContenidoDocumentoResidencia(),
                    tramite.getNombreArchivoResidencia(),
                    tramite.getTipoContenidoResidencia(),
                    "certificado_residencia.pdf");
            return true;
        }

        if (tieneContenido(tramite.getContenidoCertificadoSisben())) {
            adjuntarSiExiste(mensaje,
                    tramite.getContenidoCertificadoSisben(),
                    tramite.getNombreArchivoSisben(),
                    tramite.getTipoContenidoSisben(),
                    "certificado_sisben.pdf");
            return true;
        }

        if (tieneContenido(tramite.getContenidoCertificadoElectoral())) {
            adjuntarSiExiste(mensaje,
                    tramite.getContenidoCertificadoElectoral(),
                    tramite.getNombreArchivoElectoral(),
                    tramite.getTipoContenidoElectoral(),
                    "certificado_electoral.pdf");
            return true;
        }

        return false;
    }

    private boolean tieneContenido(byte[] contenido) {
        return contenido != null && contenido.length > 0;
    }

    @Async("mailTaskExecutor")
    public void enviarTokenRecuperacionContrasena(String correoDestino, String nombre, String token, java.time.LocalDateTime expiraEn) {
        try {
            String destino = normalizarCorreo(correoDestino);
            if (destino.isBlank()) {
                logger.warn("⚠️ Se omitió recuperación de contraseña: correo destino vacío");
                return;
            }

            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setTo(destino);
            mensaje.setFrom(Objects.requireNonNull(obtenerRemitente()));
            mensaje.setSubject("Recuperación de contraseña - Ventanilla Virtual");

            String nombreVisible = (nombre == null || nombre.isBlank()) ? "usuario" : nombre;
            String expiraTexto = expiraEn == null ? "próximos minutos" : expiraEn.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            String contenido = "Hola " + nombreVisible + ",\n\n"
                    + "Recibimos una solicitud para restablecer tu contraseña.\n"
                    + "Usa este token para continuar con el cambio:\n\n"
                    + token + "\n\n"
                    + "Este token expira: " + expiraTexto + "\n"
                    + "Si no solicitaste este cambio, puedes ignorar este correo.\n\n"
                    + "Ventanilla Virtual";

            mensaje.setText(contenido);
            enviarConReintento(() -> mailSender.send(mensaje), "recuperación contraseña", destino, "N/A");
        } catch (Exception e) {
            logger.error("❌ Error enviando token de recuperación: {}", e.getMessage(), e);
        }
    }
}

