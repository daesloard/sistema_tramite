package com.sistema.tramites.backend.usuario;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UsuarioRepository usuarioRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void run(String... args) throws Exception {
        // Verificar si los usuarios ya existen
        if (usuarioRepository.findByUsername("verificador").isEmpty()) {
            Usuario verificador = new Usuario();
            verificador.setNombreCompleto("Verificador Municipal");
            verificador.setEmail("verificador@municipio.gov.co");
            verificador.setUsername("verificador");
            verificador.setPasswordHash(passwordEncoder.encode("password123"));
            verificador.setRol(RolUsuario.VERIFICADOR);
            verificador.setActivo(true);
            usuarioRepository.save(verificador);
            System.out.println("✅ Usuario VERIFICADOR creado");
        }

        if (usuarioRepository.findByUsername("alcalde").isEmpty()) {
            Usuario alcalde = new Usuario();
            alcalde.setNombreCompleto("Alcalde de Cabuyaro");
            alcalde.setEmail("alcalde@municipio.gov.co");
            alcalde.setUsername("alcalde");
            alcalde.setPasswordHash(passwordEncoder.encode("password123"));
            alcalde.setRol(RolUsuario.ALCALDE);
            alcalde.setActivo(true);
            usuarioRepository.save(alcalde);
            System.out.println("✅ Usuario ALCALDE creado");
        }

        if (usuarioRepository.findByUsername("admin").isEmpty()) {
            Usuario admin = new Usuario();
            admin.setNombreCompleto("Administrador");
            admin.setEmail("admin@municipio.gov.co");
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode("password123"));
            admin.setRol(RolUsuario.ADMINISTRADOR);
            admin.setActivo(true);
            usuarioRepository.save(admin);
            System.out.println("✅ Usuario ADMINISTRADOR creado");
        }

        System.out.println("✅ Inicialización de datos completada");
    }
}
