package com.sistema.tramites.backend.auth;

import com.sistema.tramites.backend.usuario.Usuario;
import com.sistema.tramites.backend.usuario.UsuarioRepository;
import com.sistema.tramites.backend.util.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PasswordRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryService.class);

    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    private final ConcurrentHashMap<String, PasswordResetState> tokensByHash = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> tokenHashByUserId = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupMillis = new AtomicLong(0L);

    @Value("${app.auth.recovery.enabled:true}")
    private boolean enabled;

    @Value("${app.auth.recovery.token-ttl-minutes:30}")
    private long tokenTtlMinutes;

    @Value("${app.auth.recovery.cleanup-interval-seconds:300}")
    private long cleanupIntervalSeconds;

    public PasswordRecoveryService(UsuarioRepository usuarioRepository, EmailService emailService) {
        this.usuarioRepository = usuarioRepository;
        this.emailService = emailService;
    }

    public void solicitarRecuperacion(String usuarioOEmail, String clientIp) {
        if (!enabled) {
            return;
        }

        String dato = normalizar(usuarioOEmail);
        if (dato.isBlank()) {
            return;
        }

        Optional<Usuario> usuarioOpt = usuarioRepository.findByUsernameIgnoreCase(dato);
        if (usuarioOpt.isEmpty()) {
            usuarioOpt = usuarioRepository.findByEmailIgnoreCase(dato);
        }

        if (usuarioOpt.isEmpty()) {
            return;
        }

        Usuario usuario = usuarioOpt.get();
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            return;
        }

        String correo = normalizar(usuario.getEmail());
        if (correo.isBlank()) {
            return;
        }

        long nowMillis = System.currentTimeMillis();
        cleanupIfNeeded(nowMillis);

        String tokenPlano = generarToken();
        String tokenHash = hashToken(tokenPlano);
        LocalDateTime expira = LocalDateTime.now().plusMinutes(Math.max(1L, tokenTtlMinutes));

        String previousHash = tokenHashByUserId.put(usuario.getId(), tokenHash);
        if (previousHash != null) {
            tokensByHash.remove(previousHash);
        }

        PasswordResetState state = new PasswordResetState(
                usuario.getId(),
                usuario.getUsername(),
                correo,
                expira,
                normalizar(clientIp),
                nowMillis
        );
        tokensByHash.put(tokenHash, state);

        try {
            emailService.enviarTokenRecuperacionContrasena(correo, usuario.getNombreCompleto(), tokenPlano, expira);
        } catch (Exception ex) {
            log.warn("No se pudo enviar correo de recuperación para usuario {}: {}", usuario.getUsername(), ex.getMessage());
        }
    }

    public void restablecerContrasena(String tokenPlano, String nuevaContrasena) {
        if (!enabled) {
            throw new IllegalStateException("Recuperación de contraseña deshabilitada");
        }

        String token = normalizar(tokenPlano);
        String password = nuevaContrasena == null ? "" : nuevaContrasena.trim();

        if (token.isBlank()) {
            throw new IllegalArgumentException("Token inválido");
        }

        if (!cumplePoliticaContrasena(password)) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres, una letra y un número");
        }

        long nowMillis = System.currentTimeMillis();
        cleanupIfNeeded(nowMillis);

        String tokenHash = hashToken(token);
        PasswordResetState state = tokensByHash.get(tokenHash);
        if (state == null) {
            throw new IllegalArgumentException("Token inválido o expirado");
        }

        synchronized (state) {
            if (state.used) {
                throw new IllegalArgumentException("Token inválido o expirado");
            }
            if (state.expiresAt.isBefore(LocalDateTime.now())) {
                tokensByHash.remove(tokenHash);
                tokenHashByUserId.remove(state.usuarioId, tokenHash);
                throw new IllegalArgumentException("Token inválido o expirado");
            }

            Usuario usuario = usuarioRepository.findById(state.usuarioId)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

            usuario.setPasswordHash(passwordEncoder.encode(password));
            usuarioRepository.save(usuario);

            state.used = true;
            tokensByHash.remove(tokenHash);
            tokenHashByUserId.remove(state.usuarioId, tokenHash);
        }
    }

    private void cleanupIfNeeded(long nowMillis) {
        long intervalMillis = Math.max(1L, cleanupIntervalSeconds) * 1000L;
        long previous = lastCleanupMillis.get();
        if ((nowMillis - previous) < intervalMillis || !lastCleanupMillis.compareAndSet(previous, nowMillis)) {
            return;
        }

        tokensByHash.entrySet().removeIf(entry -> {
            PasswordResetState state = entry.getValue();
            boolean expired = state.expiresAt.isBefore(LocalDateTime.now());
            if (expired) {
                tokenHashByUserId.remove(state.usuarioId, entry.getKey());
            }
            return expired || state.used;
        });
    }

    private boolean cumplePoliticaContrasena(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }

        boolean hasLetter = false;
        boolean hasDigit = false;

        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            }
            if (Character.isDigit(c)) {
                hasDigit = true;
            }
            if (hasLetter && hasDigit) {
                return true;
            }
        }

        return false;
    }

    private String generarToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo generar hash de token", e);
        }
    }

    private String normalizar(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private static final class PasswordResetState {
        private final Long usuarioId;
        private final String username;
        private final String email;
        private final LocalDateTime expiresAt;
        private final String requestedIp;
        private final long createdAtMillis;
        private volatile boolean used;

        private PasswordResetState(Long usuarioId, String username, String email, LocalDateTime expiresAt,
                                   String requestedIp, long createdAtMillis) {
            this.usuarioId = usuarioId;
            this.username = username;
            this.email = email;
            this.expiresAt = expiresAt;
            this.requestedIp = requestedIp;
            this.createdAtMillis = createdAtMillis;
            this.used = false;
        }
    }
}
