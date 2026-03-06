package com.sistema.tramites.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsername(String username);
    Optional<Usuario> findByUsernameAndRolAndActivoTrue(String username, RolUsuario rol);
    List<Usuario> findAllByRolAndActivoTrue(RolUsuario rol);
    boolean existsByUsername(String username);

    @Transactional
    @Modifying
    @Query("update Usuario u set u.fechaUltimAcceso = :fecha where u.id = :id")
    int actualizarFechaUltimoAcceso(@Param("id") Long id, @Param("fecha") LocalDateTime fecha);
}
