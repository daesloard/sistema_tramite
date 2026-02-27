package com.sistema.tramites.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsername(String username);
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByUsernameAndRolAndActivoTrue(String username, RolUsuario rol);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
