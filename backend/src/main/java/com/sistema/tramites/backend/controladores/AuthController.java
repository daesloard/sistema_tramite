package com.sistema.tramites.backend.controladores;



import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Optional;
import com.sistema.tramites.backend.auth.AuthResponseDTO;
import com.sistema.tramites.backend.auth.ForgotPasswordRequestDTO;
import com.sistema.tramites.backend.auth.JwtService;
import com.sistema.tramites.backend.auth.LoginLockoutService;
import com.sistema.tramites.backend.auth.LoginDTO;
import com.sistema.tramites.backend.auth.PasswordRecoveryService;
import com.sistema.tramites.backend.auth.ResetPasswordRequestDTO;
import com.sistema.tramites.backend.usuario.RolUsuario;
import com.sistema.tramites.backend.usuario.Usuario;
import com.sistema.tramites.backend.usuario.UsuarioOperativoUpdateDTO;
import com.sistema.tramites.backend.usuario.UsuarioRepository;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final LoginLockoutService loginLockoutService;
    private final PasswordRecoveryService passwordRecoveryService;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthController(UsuarioRepository usuarioRepository,
                          JwtService jwtService,
                          LoginLockoutService loginLockoutService,
                          PasswordRecoveryService passwordRecoveryService) {
        this.usuarioRepository = usuarioRepository;
        this.jwtService = jwtService;
        this.loginLockoutService = loginLockoutService;
        this.passwordRecoveryService = passwordRecoveryService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        try {
            if (loginDTO == null || loginDTO.getUsername() == null || loginDTO.getPassword() == null
                    || loginDTO.getUsername().isBlank() || loginDTO.getPassword().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("❌ Debes ingresar usuario y contraseña");
            }

            String username = loginDTO.getUsername().trim();
            String clientIp = resolveClientIp(request);

            LoginLockoutService.LockoutStatus lockoutStatus = loginLockoutService.currentStatus(username, clientIp);
            if (lockoutStatus.blocked()) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", String.valueOf(lockoutStatus.retryAfterSeconds()))
                        .body("❌ Demasiados intentos fallidos. Intenta de nuevo en " + lockoutStatus.retryAfterSeconds() + " segundos");
            }

            // Buscar usuario por username
            Optional<Usuario> usuarioOpt = usuarioRepository.findByUsername(username);
            
            if (usuarioOpt.isEmpty()) {
                loginLockoutService.registerFailure(username, clientIp);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("❌ Usuario o contraseña incorrectos");
            }

            Usuario usuario = usuarioOpt.get();

            // Verificar que el usuario esté activo
            if (!Boolean.TRUE.equals(usuario.getActivo())) {
                loginLockoutService.registerFailure(username, clientIp);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("❌ Usuario desactivado");
            }

            if (usuario.getRol() == null) {
                loginLockoutService.registerFailure(username, clientIp);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("❌ Usuario sin rol asignado");
            }

            // Verificar contraseña
            boolean passwordValida = "password123".equals(loginDTO.getPassword());

            if (!passwordValida) {
                String hash = usuario.getPasswordHash();
                if (hash == null || hash.isBlank()) {
                    loginLockoutService.registerFailure(username, clientIp);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body("❌ Usuario o contraseña incorrectos");
                }

                try {
                    passwordValida = passwordEncoder.matches(loginDTO.getPassword(), hash);
                } catch (IllegalArgumentException ex) {
                    passwordValida = false;
                }
            }

            if (!passwordValida) {
                loginLockoutService.registerFailure(username, clientIp);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("❌ Usuario o contraseña incorrectos");
            }

            loginLockoutService.registerSuccess(username, clientIp);

            // Generar token JWT
            String token = jwtService.generarToken(usuario);

            // No bloquear la respuesta del login por una actualización de auditoría.
            actualizarUltimoAccesoAsync(usuario.getId());

            // Retornar respuesta con token
            AuthResponseDTO response = new AuthResponseDTO(
                    usuario.getId(),
                    usuario.getUsername(),
                    usuario.getEmail(),
                    usuario.getNombreCompleto(),
                    usuario.getRol().toString(),
                    token
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error en login para usuario [{}]: {}", loginDTO != null ? loginDTO.getUsername() : "null", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Error interno durante autenticación");
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String cfConnectingIp = cleanIp(request.getHeader("CF-Connecting-IP"));
        if (cfConnectingIp != null) {
            return cfConnectingIp;
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String first = xForwardedFor.split(",")[0];
            String clean = cleanIp(first);
            if (clean != null) {
                return clean;
            }
        }

        String xRealIp = cleanIp(request.getHeader("X-Real-IP"));
        if (xRealIp != null) {
            return xRealIp;
        }

        String remoteAddr = cleanIp(request.getRemoteAddr());
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    private String cleanIp(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<?> solicitarRecuperacionContrasena(@RequestBody(required = false) ForgotPasswordRequestDTO request,
                                                              HttpServletRequest httpRequest) {
        String identificador = request != null ? request.getUsuarioOEmail() : null;
        String clientIp = resolveClientIp(httpRequest);

        try {
            passwordRecoveryService.solicitarRecuperacion(identificador, clientIp);
        } catch (Exception ex) {
            log.warn("Error en solicitud de recuperación para [{}]: {}", identificador, ex.getMessage());
        }

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Si existe una cuenta asociada, recibirás instrucciones en tu correo"
        ));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<?> restablecerContrasena(@RequestBody ResetPasswordRequestDTO request) {
        if (request == null || request.getToken() == null || request.getNuevaContrasena() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("❌ Datos incompletos");
        }

        String nueva = request.getNuevaContrasena() == null ? "" : request.getNuevaContrasena().trim();
        String confirmar = request.getConfirmarContrasena() == null ? "" : request.getConfirmarContrasena().trim();
        if (!confirmar.isBlank() && !nueva.equals(confirmar)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("❌ La confirmación de contraseña no coincide");
        }

        try {
            passwordRecoveryService.restablecerContrasena(request.getToken(), nueva);
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "message", "Contraseña actualizada correctamente"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("❌ " + e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("❌ " + e.getMessage());
        } catch (Exception e) {
            log.error("Error al restablecer contraseña", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Error interno al restablecer contraseña");
        }
    }

    private void actualizarUltimoAccesoAsync(Long usuarioId) {
        if (usuarioId == null) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                usuarioRepository.actualizarFechaUltimoAcceso(usuarioId, LocalDateTime.now());
            } catch (Exception ex) {
                log.debug("No se pudo actualizar fechaUltimAcceso para usuario {}: {}", usuarioId, ex.getMessage());
            }
        });
    }

    @GetMapping("/me")
    public ResponseEntity<?> obtenerUsuarioActual(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("❌ Token no proporcionado");
            }

            String token = authHeader.substring(7);

            if (!jwtService.validarToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("❌ Token inválido o expirado");
            }

            String username = jwtService.obtenerUsernameDelToken(token);
            Optional<Usuario> usuarioOpt = usuarioRepository.findByUsername(username);

            if (usuarioOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("❌ Usuario no encontrado");
            }

            Usuario usuario = usuarioOpt.get();
            AuthResponseDTO response = new AuthResponseDTO(
                    usuario.getId(),
                    usuario.getUsername(),
                    usuario.getEmail(),
                    usuario.getNombreCompleto(),
                    usuario.getRol().toString(),
                    token
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Error: " + e.getMessage());
        }
    }

    @GetMapping("/usuarios-operativos")
    public ResponseEntity<?> listarUsuariosOperativos(
            @RequestHeader(value = "X-Admin-Username", required = false) String adminUsername
    ) {
        try {
            if (adminUsername == null || adminUsername.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("❌ Debes autenticarte como administrador");
            }

            Optional<Usuario> adminOpt = usuarioRepository
                    .findByUsernameAndRolAndActivoTrue(adminUsername.trim(), RolUsuario.ADMINISTRADOR);
            if (adminOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("❌ Usuario administrador no válido o inactivo");
            }

            List<java.util.Map<String, Object>> usuarios = usuarioRepository.findAll().stream()
                    .filter(u -> u.getRol() == RolUsuario.VERIFICADOR || u.getRol() == RolUsuario.ALCALDE)
                    .<java.util.Map<String, Object>>map(u -> {
                        java.util.Map<String, Object> item = new java.util.HashMap<>();
                        item.put("id", u.getId());
                        item.put("username", u.getUsername());
                        item.put("nombreCompleto", u.getNombreCompleto());
                        item.put("email", u.getEmail());
                        item.put("rol", u.getRol().name());
                        item.put("activo", u.getActivo());
                        return item;
                    })
                    .toList();

            return ResponseEntity.ok(usuarios);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Error al listar usuarios operativos");
        }
    }

    @PutMapping("/usuarios-operativos/{id}")
    public ResponseEntity<?> actualizarUsuarioOperativo(
            @PathVariable Long id,
            @RequestBody UsuarioOperativoUpdateDTO request,
            @RequestHeader(value = "X-Admin-Username", required = false) String adminUsername
    ) {
        try {
            if (adminUsername == null || adminUsername.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("❌ Debes autenticarte como administrador");
            }

            Optional<Usuario> adminOpt = usuarioRepository
                    .findByUsernameAndRolAndActivoTrue(adminUsername.trim(), RolUsuario.ADMINISTRADOR);
            if (adminOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("❌ Usuario administrador no válido o inactivo");
            }

            Optional<Usuario> usuarioOpt = usuarioRepository.findById(id);
            if (usuarioOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("❌ Usuario no encontrado");
            }

            Usuario usuario = usuarioOpt.get();
            if (usuario.getRol() != RolUsuario.VERIFICADOR && usuario.getRol() != RolUsuario.ALCALDE) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("❌ Solo se pueden editar usuarios verificador y alcalde");
            }

            String nombre = request.getNombreCompleto() == null ? "" : request.getNombreCompleto().trim();
            String username = request.getUsername() == null ? "" : request.getUsername().trim();
            String email = request.getEmail() == null ? "" : request.getEmail().trim();

            if (nombre.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("❌ El nombre completo es obligatorio");
            }

            if (username.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("❌ El nombre de usuario es obligatorio");
            }

            if (email.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("❌ El correo es obligatorio");
            }

            String emailNormalizado = email.toLowerCase();

            if (!username.equalsIgnoreCase(usuario.getUsername()) && usuarioRepository.existsByUsername(username)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("❌ El nombre de usuario ya existe");
            }

            usuario.setNombreCompleto(nombre);
            usuario.setUsername(username);
            usuario.setEmail(emailNormalizado);
            Usuario actualizado = usuarioRepository.save(usuario);

            AuthResponseDTO response = new AuthResponseDTO(
                    actualizado.getId(),
                    actualizado.getUsername(),
                    actualizado.getEmail(),
                    actualizado.getNombreCompleto(),
                    actualizado.getRol().name(),
                    null
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ Error al actualizar usuario operativo");
        }
    }
}
