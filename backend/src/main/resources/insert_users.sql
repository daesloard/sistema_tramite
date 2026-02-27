-- Insertar usuarios iniciales
INSERT INTO tramites_municipales.usuarios (nombre_completo, email, username, password_hash, rol, activo) VALUES 
('Verificador Municipal', 'verificador@municipio.gov.co', 'verificador', 'password123', 'VERIFICADOR', 1),
('Alcalde de Cabuyaro', 'alcalde@municipio.gov.co', 'alcalde', 'password123', 'ALCALDE', 1),
('Administrador', 'admin@municipio.gov.co', 'admin', 'password123', 'ADMINISTRADOR', 1);
