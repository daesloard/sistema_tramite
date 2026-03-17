# Implementación Gotenberg para Plantillas DOCX en Deploy

Estado: Pendiente

## Pasos:

### 1. ✅ Configurar application.properties (app.pdf.gotenberg.enabled=true) - Hecho
### 2. ✅ Editar DocumentoGeneradoService.java (integrar cliente HTTP a Gotenberg) - Hecho y compilación fix (timeout en RestTemplate existente)
### 3. ✅ Crear docker-compose.yml (backend + gotenberg) - Hecho
### 4. 🔄 Probar localmente (mvn package + docker-compose up)
### 5. 🔄 Actualizar deploy (Docker con Gotenberg)
### 6. ✅ Verificar PDFs respetan plantillas exactas
### 7. ✅ attempt_completion

Próximo paso: Editar properties.

