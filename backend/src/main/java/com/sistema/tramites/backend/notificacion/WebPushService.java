package com.sistema.tramites.backend.notificacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WebPushService {

    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);

    private final WebPushSubscriptionRepository webPushSubscriptionRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.webpush.enabled:false}")
    private boolean webPushEnabled;

    @Value("${app.webpush.public-key:}")
    private String publicKey;

    @Value("${app.webpush.private-key:}")
    private String privateKey;

    @Value("${app.webpush.subject:mailto:sistemas@cabuyaro-meta.gov.co}")
    private String subject;

    public WebPushService(WebPushSubscriptionRepository webPushSubscriptionRepository,
                          ObjectMapper objectMapper) {
        this.webPushSubscriptionRepository = webPushSubscriptionRepository;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return webPushEnabled
                && publicKey != null && !publicKey.isBlank()
                && privateKey != null && !privateKey.isBlank();
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void registrarSuscripcion(Long usuarioId, WebPushSubscribeRequestDTO request, String userAgent) {
        if (usuarioId == null || request == null || request.getEndpoint() == null || request.getEndpoint().isBlank()) {
            return;
        }

        if (request.getKeys() == null || request.getKeys().getP256dh() == null || request.getKeys().getAuth() == null) {
            return;
        }

        String endpoint = request.getEndpoint().trim();
        String p256dh = request.getKeys().getP256dh().trim();
        String auth = request.getKeys().getAuth().trim();

        Optional<WebPushSubscription> existente = webPushSubscriptionRepository.findByUsuarioIdAndEndpoint(usuarioId, endpoint);
        WebPushSubscription subscription = existente.orElseGet(WebPushSubscription::new);

        subscription.setUsuarioId(usuarioId);
        subscription.setEndpoint(endpoint);
        subscription.setP256dh(p256dh);
        subscription.setAuth(auth);
        subscription.setUserAgent(userAgent == null ? null : userAgent.trim());
        subscription.setActivo(true);

        if (subscription.getFechaCreacion() == null) {
            subscription.setFechaCreacion(LocalDateTime.now());
        }
        subscription.setFechaActualizacion(LocalDateTime.now());
        webPushSubscriptionRepository.save(subscription);
    }

    public void desactivarSuscripcion(Long usuarioId, String endpoint) {
        if (usuarioId == null || endpoint == null || endpoint.isBlank()) {
            return;
        }

        webPushSubscriptionRepository.findByUsuarioIdAndEndpoint(usuarioId, endpoint.trim())
                .ifPresent(subscription -> {
                    subscription.setActivo(false);
                    subscription.setFechaActualizacion(LocalDateTime.now());
                    webPushSubscriptionRepository.save(subscription);
                });
    }

    @Async("pushTaskExecutor")
    public void enviarNotificacionAUsuario(Long usuarioId, String titulo, String mensaje, String tipo, Long tramiteId) {
        if (usuarioId == null || titulo == null || titulo.isBlank() || mensaje == null || mensaje.isBlank()) {
            return;
        }

        if (!isEnabled()) {
            return;
        }

        List<WebPushSubscription> suscripciones = webPushSubscriptionRepository.findAllByUsuarioIdAndActivoTrue(usuarioId);
        if (suscripciones.isEmpty()) {
            return;
        }

        PushService pushService;
        try {
            pushService = construirPushService();
        } catch (Exception ex) {
            log.warn("No fue posible inicializar servicio Web Push: {}", ex.getMessage());
            return;
        }

        for (WebPushSubscription subscription : suscripciones) {
            try {
                String payload = construirPayload(titulo, mensaje, tipo, tramiteId);
                Notification notification = new Notification(
                        subscription.getEndpoint(),
                        subscription.getP256dh(),
                        subscription.getAuth(),
                        payload
                );

                HttpResponse respuesta = pushService.send(notification);
                int status = respuesta.getStatusLine().getStatusCode();

                if (status == 404 || status == 410) {
                    subscription.setActivo(false);
                    subscription.setFechaActualizacion(LocalDateTime.now());
                    webPushSubscriptionRepository.save(subscription);
                }
            } catch (Exception ex) {
                log.debug("Falló envío Web Push para usuario {} endpoint {}: {}",
                        usuarioId,
                        subscription.getEndpoint(),
                        ex.getMessage());
            }
        }
    }

    private PushService construirPushService() throws Exception {
        PushService pushService = new PushService();
        pushService.setPublicKey(Utils.loadPublicKey(publicKey));
        pushService.setPrivateKey(Utils.loadPrivateKey(privateKey));
        pushService.setSubject(subject);
        return pushService;
    }

    private String construirPayload(String titulo, String mensaje, String tipo, Long tramiteId) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", titulo);
        payload.put("body", mensaje);
        payload.put("type", tipo == null ? "INFO" : tipo);
        payload.put("tramiteId", tramiteId);
        payload.put("url", "/panel");
        payload.put("timestamp", System.currentTimeMillis());
        return objectMapper.writeValueAsString(payload);
    }
}
