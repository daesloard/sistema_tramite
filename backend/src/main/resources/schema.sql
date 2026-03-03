-- ========================================
-- BASE DE DATOS: SISTEMA DE TRÁMITES MUNICIPALES
-- ========================================

-- Crear base de datos
CREATE DATABASE IF NOT EXISTS tramites_municipales;
USE tramites_municipales;

-- ========================================
-- TABLA: USUARIOS (Verificadores y Alcaldes)
-- ========================================
CREATE TABLE IF NOT EXISTS usuarios (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    nombre_completo VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(500) NOT NULL,
    rol ENUM('VERIFICADOR', 'ALCALDE', 'ADMINISTRADOR') NOT NULL DEFAULT 'VERIFICADOR',
    activo BOOLEAN DEFAULT TRUE,
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    fecha_ultim_acceso TIMESTAMP NULL,
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_rol (rol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- TABLA: TRAMITES (Solicitudes de Certificado)
-- ========================================
CREATE TABLE IF NOT EXISTS tramites (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    
    -- Identificación del trámite
    numero_radicado VARCHAR(50) NOT NULL UNIQUE,
    tipo_tramite VARCHAR(100) NOT NULL,
    estado ENUM('RADICADO', 'EN_VALIDACION', 'EN_FIRMA', 'FINALIZADO', 'RECHAZADO') NOT NULL DEFAULT 'RADICADO',
    
    -- Información del solicitante
    nombre_solicitante VARCHAR(255) NOT NULL,
    tipo_documento VARCHAR(50) NOT NULL,
    numero_documento VARCHAR(50) NOT NULL UNIQUE,
    lugar_expedicion_documento VARCHAR(120) NOT NULL,
    direccion_residencia VARCHAR(500) NOT NULL,
    barrio_residencia VARCHAR(120) NOT NULL,
    correo_electronico VARCHAR(255) NOT NULL,
    telefono VARCHAR(20) NOT NULL,
    
    -- Información del certificado
    tipo_certificado VARCHAR(100),
    dias_vigencia INT DEFAULT 180,
    
    -- Documentos
    ruta_documento_solicitud VARCHAR(500),
    ruta_documento_identidad VARCHAR(500),
    ruta_certificado_sisben VARCHAR(500),
    ruta_certificado_electoral VARCHAR(500),
    ruta_certificado_final VARCHAR(500),
    drive_folder_id VARCHAR(255),
    
    -- Fechas
    fecha_radicacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_vencimiento DATE,
    fecha_vigencia DATE,
    fecha_modificacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Verificación y firma
    observaciones TEXT,
    consecutivo_verificador VARCHAR(100),
    firma_alcalde VARCHAR(500),
    fecha_firma_alcalde DATETIME,
    codigo_verificacion VARCHAR(120) UNIQUE,
    hash_documento_generado VARCHAR(128),
    contenido_pdf_generado LONGBLOB,
    nombre_pdf_generado VARCHAR(255),
    tipo_contenido_pdf_generado VARCHAR(120),
    usuario_verificador_id BIGINT,
    usuario_alcalde_id BIGINT,
    
    -- Metadata
    fecha_ultima_modificacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_numero_radicado (numero_radicado),
    INDEX idx_estado (estado),
    INDEX idx_numero_documento (numero_documento),
    INDEX idx_codigo_verificacion (codigo_verificacion),
    INDEX idx_correo (correo_electronico),
    INDEX idx_usuario_verificador (usuario_verificador_id),
    INDEX idx_usuario_alcalde (usuario_alcalde_id),
    FOREIGN KEY (usuario_verificador_id) REFERENCES usuarios(id) ON DELETE SET NULL,
    FOREIGN KEY (usuario_alcalde_id) REFERENCES usuarios(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- TABLA: AUDITORIA
-- ========================================
CREATE TABLE IF NOT EXISTS auditoria_tramites (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tramite_id BIGINT NOT NULL,
    usuario_id BIGINT,
    accion VARCHAR(100) NOT NULL,
    descripcion TEXT,
    estado_anterior VARCHAR(50),
    estado_nuevo VARCHAR(50),
    fecha_integracion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_tramite (tramite_id),
    INDEX idx_usuario (usuario_id),
    INDEX idx_fecha (fecha_integracion),
    FOREIGN KEY (tramite_id) REFERENCES tramites(id) ON DELETE CASCADE,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- DATOS INICIALES: USUARIOS
-- ========================================
INSERT INTO usuarios (nombre_completo, email, username, password_hash, rol) VALUES 
('Verificador Municipal', 'verificador@municipio.gov.co', 'verificador', '$2a$10$slYQmyNdGzin7olVN3p5Be7DXH0NZMp.0JcgkxU3fsVxXeUI/QHMG', 'VERIFICADOR'),
('Alcalde de Cabuyaro', 'alcalde@municipio.gov.co', 'alcalde', '$2a$10$slYQmyNdGzin7olVN3p5Be7DXH0NZMp.0JcgkxU3fsVxXeUI/QHMG', 'ALCALDE'),
('Administrador', 'admin@municipio.gov.co', 'admin', '$2a$10$slYQmyNdGzin7olVN3p5Be7DXH0NZMp.0JcgkxU3fsVxXeUI/QHMG', 'ADMINISTRADOR');

-- Contraseña por defecto: "password123" (hasheada con bcrypt)
-- Cambiar en producción

-- ========================================
-- OBSERVACIONES:
-- ========================================
-- 1. Las contraseñas están hasheadas con bcrypt
-- 2. Contraseña por defecto: "password123"
-- 3. CAMBIAR EN PRODUCCIÓN
-- 4. El UUID se genera automáticamente en la aplicación
-- 5. Los campos de ruta de documentos pueden ser URLs o paths locales
