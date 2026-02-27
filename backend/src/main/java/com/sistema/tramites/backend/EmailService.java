package com.sistema.tramites.backend;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    
    @Value("${spring.mail.username}")
    private String emailRemitente;

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

    public void enviarConfirmacionRadicacion(String correoDestino, String nombre,
                                             String numeroRadicado, LocalDate fechaVencimiento,
                                             Tramite tramite) {
        try {
            logger.info("📧 Enviando email de confirmación a: {}", correoDestino);
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper mensaje = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            mensaje.setTo(correoDestino);
            mensaje.setFrom(emailRemitente);
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

                    if (tramite != null) {
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

                    mailSender.send(mimeMessage);
            logger.info("✅ Email de confirmación enviado exitosamente a: {}", correoDestino);
            
        } catch (Exception e) {
            logger.error("❌ Error al enviar email de confirmación: {}", e.getMessage(), e);
        }
    }

    /**
     * Envía email al verificador notificando nueva solicitud
     */
    public void enviarNotificacionVerificador(String correoVerificador, String numeroRadicado, 
                                              String nombreSolicitante) {
        try {
            logger.info("📧 Enviando notificación de verificación a: {}", correoVerificador);
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setTo(correoVerificador);
            mensaje.setFrom(emailRemitente);
            mensaje.setSubject("Nueva Solicitud de Verificación - " + numeroRadicado);
            
            String contenido = "Nueva solicitud de verificación:\n\n" +
                    "Número de radicado: " + numeroRadicado + "\n" +
                    "Nombre del solicitante: " + nombreSolicitante + "\n" +
                    "Tipo de trámite: Certificado de Residencia\n\n" +
                    "Por favor, inicia sesión en el portal para revisar la documentación " +
                    "y completar el checklist de verificación.\n\n" +
                    "Ventanilla Virtual";
            
            mensaje.setText(contenido);
            mailSender.send(mensaje);
            logger.info("✅ Notificación de verificación enviada a: {}", correoVerificador);
            
        } catch (Exception e) {
            logger.error("❌ Error al enviar email de verificador: {}", e.getMessage(), e);
        }
    }

    /**
     * Envía email con documento final (aprobado o rechazado)
     */
    public void enviarDocumentoFinal(String correoDestino, String nombre, String numeroRadicado, 
                                     boolean aprobado, String observaciones) {
        enviarDocumentoFinal(correoDestino, nombre, numeroRadicado, aprobado, observaciones, null, null);
    }

    public void enviarDocumentoFinal(String correoDestino, String nombre, String numeroRadicado,
                                     boolean aprobado, String observaciones,
                                     byte[] adjuntoPdf, String nombreAdjunto) {
        try {
            String estado = aprobado ? "APROBADO" : "RECHAZADO";
            logger.info("📧 Enviando email de resultado ({}) a: {}", estado, correoDestino);
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper mensaje = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            mensaje.setTo(correoDestino);
            mensaje.setFrom(emailRemitente);
            
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

            if (adjuntoPdf != null && adjuntoPdf.length > 0) {
                String nombreArchivoAdjunto = (nombreAdjunto != null && !nombreAdjunto.isBlank())
                        ? nombreAdjunto
                        : "respuesta_solicitud_" + numeroRadicado + ".pdf";
                mensaje.addAttachment(nombreArchivoAdjunto, new ByteArrayResource(adjuntoPdf), "application/pdf");
            }

            mailSender.send(mimeMessage);
            logger.info("✅ Email de resultado ({}) enviado a: {}", estado, correoDestino);
            
        } catch (Exception e) {
            logger.error("❌ Error al enviar email de resultado: {}", e.getMessage(), e);
        }
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

        mensaje.addAttachment(nombreFinal, new ByteArrayResource(contenido), tipoFinal);
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
}

