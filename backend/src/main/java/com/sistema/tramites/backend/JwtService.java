package com.sistema.tramites.backend;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret:MiClaveSecretaMuySeguraParaJWTDeTramitesQueTieneMasde32Caracteres}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")  // 24 horas por defecto
    private Long jwtExpiration;

    private Key obtenerClaveFirma() {
        try {
            byte[] secretBytes = (jwtSecret == null ? "" : jwtSecret).getBytes(StandardCharsets.UTF_8);
            byte[] keyBytes = MessageDigest.getInstance("SHA-512").digest(secretBytes);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo inicializar la clave JWT", e);
        }
    }

    public String generarToken(Usuario usuario) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .setSubject(usuario.getUsername())
                .claim("userId", usuario.getId())
                .claim("email", usuario.getEmail())
                .claim("rol", usuario.getRol().toString())
                .claim("nombreCompleto", usuario.getNombreCompleto())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(obtenerClaveFirma(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String obtenerUsernameDelToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(obtenerClaveFirma())
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean validarToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(obtenerClaveFirma())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
