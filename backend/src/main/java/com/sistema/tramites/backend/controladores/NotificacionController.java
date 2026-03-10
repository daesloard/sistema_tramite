package com.sistema.tramites.backend.controladores;

import com.sistema.tramites.backend.notificacion.NotificacionUsuario;
import com.sistema.tramites.backend.notificacion.NotificacionUsuarioService;
import com.sistema.tramites.backend.usuario.Usuario;
import com.sistema.tramites.backend.usuario.UsuarioRepository;
import com.sistema.tramites.backend.notificacion.WebPushService;
import com.sistema.tramites.backend.notificacion.WebPushSubscribeRequestDTO;
import com.sistema.tramites.backend.notificacion.WebPushUnsubscribeRequestDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private final UsuarioRepository usuarioRepository;
    private final NotificacionUsuarioService notificacionUsuarioService;
    private final WebPushService webPushService;

    public NotificacionController(UsuarioRepository usuarioRepository,
                                  NotificacionUsuarioService notificacionUsuarioService,
                                  WebPushService webPushService) {
        this.usuarioRepository = usuarioRepository;
        this.notificacionUsuarioService = notificacionUsuarioService;
        this.webPushService = webPushService;
    }

    @GetMapping("/webpush/public-key")
    public ResponseEntity<?> obtenerLlavePublicaWebPush() {
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("enabled", webPushService.isEnabled());
        respuesta.put("publicKey", webPushService.getPublicKey());
        return ResponseEntity.ok(respuesta);
    }

    @PostMapping("/webpush/subscribe")
    public ResponseEntity<?> registrarSuscripcionWebPush(
            @RequestBody(required = false) WebPushSubscribeRequestDTO request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Username", required = false) String usernameHeader,
            @RequestHeader(value = "X-Admin-Username", required = false) String adminUsernameHeader) {
        Optional<Usuario> usuarioOpt = resolverUsuarioPorHeaders(usernameHeader, adminUsernameHeader);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("❌ Usuario no válido o inactivo");
        }

        Usuario usuario = usuarioOpt.get();
        webPushService.registrarSuscripcion(usuario.getId(), request, userAgent);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/webpush/subscribe")
    public ResponseEntity<?> desactivarSuscripcionWebPush(
            @RequestBody(required = false) WebPushUnsubscribeRequestDTO request,
            @RequestHeader(value = "X-Username", required = false) String usernameHeader,
            @RequestHeader(value = "X-Admin-Username", required = false) String adminUsernameHeader) {
        Optional<Usuario> usuarioOpt = resolverUsuarioPorHeaders(usernameHeader, adminUsernameHeader);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("❌ Usuario no válido o inactivo");
        }

        String endpoint = request != null ? request.getEndpoint() : null;
        webPushService.desactivarSuscripcion(usuarioOpt.get().getId(), endpoint);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/webpush/test")
    public ResponseEntity<?> enviarPushPrueba(
            @RequestBody(required = false) Map<String, String> payload,
            @RequestHeader(value = "X-Username", required = false) String usernameHeader,
            @RequestHeader(value = "X-Admin-Username", required = false) String adminUsernameHeader) {
        Optional<Usuario> usuarioOpt = resolverUsuarioPorHeaders(usernameHeader, adminUsernameHeader);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("❌ Usuario no válido o inactivo");
        }

        Usuario usuario = usuarioOpt.get();
        String titulo = payload != null && payload.get("titulo") != null && !payload.get("titulo").isBlank()
                ? payload.get("titulo").trim()
                : "Prueba de notificaciones push";
        String mensaje = payload != null && payload.get("mensaje") != null && !payload.get("mensaje").isBlank()
                ? payload.get("mensaje").trim()
                : "Push activo para " + usuario.getUsername() + " en Sistema de Tramites.";

        notificacionUsuarioService.crearNotificacion(usuario.getId(), null, titulo, mensaje, "INFO");
        return ResponseEntity.ok(Map.of("ok", true, "mensaje", "Notificación de prueba enviada"));
    }

    @GetMapping("/mis")
    public ResponseEntity<?> listarMisNotificaciones(
            @RequestHeader(value = "X-Username", required = false) String usernameHeader,
            @RequestHeader(value = "X-Admin-Username", required = false) String adminUsernameHeader) {
        Optional<Usuario> usuarioOpt = resolverUsuarioPorHeaders(usernameHeader, adminUsernameHeader);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("❌ Usuario no válido o inactivo");
        }

        Usuario usuario = usuarioOpt.get();
        List<NotificacionUsuario> notificaciones = notificacionUsuarioService.listarPorUsuario(usuario.getId());
        long noLeidas = notificacionUsuarioService.contarNoLeidas(usuario.getId());

        List<Map<String, Object>> items = notificaciones.stream().map(n -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", n.getId());
            item.put("tramiteId", n.getTramiteId());
            item.put("titulo", n.getTitulo());
            item.put("mensaje", n.getMensaje());
            item.put("tipo", n.getTipo());
            item.put("leida", n.getLeida());
            item.put("fechaCreacion", n.getFechaCreacion());
            item.put("fechaLectura", n.getFechaLectura());
            return item;
        }).toList();

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("usuarioId", usuario.getId());
        respuesta.put("username", usuario.getUsername());
        respuesta.put("total", items.size());
        respuesta.put("noLeidas", noLeidas);
        respuesta.put("notificaciones", items);
        return ResponseEntity.ok(respuesta);
    }

    @PostMapping("/{id}/leer")
    public ResponseEntity<?> marcarLeida(
            @PathVariable Long id,
            @RequestHeader(value = "X-Username", required = false) String usernameHeader,
            @RequestHeader(value = "X-Admin-Username", required = false) String adminUsernameHeader) {
        Optional<Usuario> usuarioOpt = resolverUsuarioPorHeaders(usernameHeader, adminUsernameHeader);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("❌ Usuario no válido o inactivo");
        }

        boolean ok = notificacionUsuarioService.marcarLeida(id, usuarioOpt.get().getId());
        if (!ok) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("❌ Notificación no encontrada");
        }

        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/leer-todas")
    public ResponseEntity<?> marcarTodasLeidas(
            @RequestHeader(value = "X-Username", required = false) String usernameHeader,
            @RequestHeader(value = "X-Admin-Username", required = false) String adminUsernameHeader) {
        Optional<Usuario> usuarioOpt = resolverUsuarioPorHeaders(usernameHeader, adminUsernameHeader);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("❌ Usuario no válido o inactivo");
        }

        int actualizadas = notificacionUsuarioService.marcarTodasLeidas(usuarioOpt.get().getId());
        return ResponseEntity.ok(Map.of("ok", true, "actualizadas", actualizadas));
    }

    private Optional<Usuario> resolverUsuarioPorHeaders(String usernameHeader, String adminUsernameHeader) {
        String username = null;
        if (usernameHeader != null && !usernameHeader.isBlank()) {
            username = usernameHeader.trim();
        } else if (adminUsernameHeader != null && !adminUsernameHeader.isBlank()) {
            username = adminUsernameHeader.trim();
        }

        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        return usuarioRepository.findByUsername(username)
                .filter(usuario -> Boolean.TRUE.equals(usuario.getActivo()));
    }
}
