package com.sistema.tramites.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificacionUsuarioRepository extends JpaRepository<NotificacionUsuario, Long> {
    List<NotificacionUsuario> findTop100ByUsuarioIdOrderByFechaCreacionDesc(Long usuarioId);
    long countByUsuarioIdAndLeidaFalse(Long usuarioId);
    Optional<NotificacionUsuario> findByIdAndUsuarioId(Long id, Long usuarioId);
    List<NotificacionUsuario> findByUsuarioIdAndLeidaFalse(Long usuarioId);
}
