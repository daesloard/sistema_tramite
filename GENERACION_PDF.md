# Generación de PDF con docxtemplater + Gotenberg

## Flujo de Procesamiento

```
┌─────────────────────────────────────────────────────────────┐
│  1. docxtemplater-service (Node.js:3001)                   │
│     - Recibe: DOCX plantilla + JSON con datos              │
│     - Reemplaza marcadores {{ }} SIN modificar formato     │
│     - Devuelve: DOCX procesado                              │
│     ↓                                                       │
│  2. Gotenberg (Docker:3000)                                 │
│     - Recibe: DOCX procesado                                │
│     - Convierte: DOCX → PDF con LibreOffice                │
│     - Devuelve: PDF idéntico a plantilla original          │
└─────────────────────────────────────────────────────────────┘
```

## Requisitos para Producción (Online)

1. Servicios accesibles por red (interna o pública) para:
   - `docxtemplater-service`
   - `Gotenberg`
2. Backend configurado con URLs remotas (sin `localhost`) en variables de entorno.
3. Opcional: **Docker** si vas a desplegar todo como contenedores.

## Modos de despliegue online

### A) Todo en contenedores (backend + docxtemplater + gotenberg)

Si tu backend corre dentro del mismo cluster/red Docker, usa nombres de servicio internos:

- `APP_DOCXTEMPLATER_URL=http://docxtemplater:3001/render-docx`
- `APP_PDF_GOTENBERG_URL=http://gotenberg:3000/forms/libreoffice/convert`

### B) Servicios gestionados/remotos

Si backend corre en nube y docxtemplater/gotenberg en otra infraestructura, usa URLs HTTPS públicas o privadas enrutable:

- `APP_DOCXTEMPLATER_URL=https://docx-render.tu-dominio.com/render-docx`
- `APP_PDF_GOTENBERG_URL=https://pdf-convert.tu-dominio.com/forms/libreoffice/convert`

El backend ahora valida al arrancar que estas URLs existan y que no apunten a `localhost` (salvo desarrollo local explícito).
   - `backend` (Java/Spring Boot)
   - `gotenberg` (conversión DOCX → PDF)
   - `docxtemplater` (procesamiento de marcadores)

## Despliegue en Producción

### 1. Construir y levantar servicios

```bash
docker-compose up -d --build
```

Este compose ya integra el arranque de los 3 servicios con healthchecks:
- backend espera a `mysql`, `docxtemplater` y `gotenberg` en estado saludable.
- `gotenberg` se construye con `Dockerfile.gotenberg` para instalar las fuentes desde `backend/src/main/resources/fonts`.

Esto levanta:
- Backend en `http://localhost:8080`
- Gotenberg en `http://localhost:3000`
- docxtemplater en `http://localhost:3001`
- MySQL en `http://localhost:3306`

### 2. Verificar que los servicios estén corriendo

```bash
docker-compose ps
```

### 3. Probar generación de PDF

Llama al endpoint de tu aplicación que genera PDFs. El flujo automático es:

1. Backend → docxtemplater (rellena marcadores)
2. Backend → Gotenberg (convierte a PDF)

## Variables de Entorno

### Backend (Spring Boot)

```bash
# URL de Gotenberg (obligatoria en despliegue online)
APP_PDF_GOTENBERG_URL=http://gotenberg:3000/forms/libreoffice/convert

# URL de docxtemplater (obligatoria en despliegue online)
APP_DOCXTEMPLATER_URL=http://docxtemplater:3001/render-docx

# Seguridad: bloquear localhost en producción
APP_PDF_ALLOW_LOCALHOST_SERVICES=false

# Habilitar Gotenberg
app.pdf.gotenberg.enabled=true
```

## Estructura de Plantillas

Las plantillas DOCX deben estar en:
```
backend/src/main/resources/templates/
```

### Marcadores Soportados

Usan sintaxis `{{nombre}}`:

| Marcador | Descripción |
|----------|-------------|
| `{{consecutivo}}` | Número consecutivo del verificador |
| `{{nombreSolicitante}}` | Nombre completo del solicitante |
| `{{numeroDocumento}}` | Número de documento |
| `{{lugarExpedicionDocumento}}` | Lugar de expedición |
| `{{direccionResidencia}}` | Dirección de residencia |
| `{{dias}}` | Día del mes (número) |
| `{{diasLetras}}` | Día del mes (letras) |
| `{{mesLetras}}` | Mes (letras) |
| `{{año}}` | Año (número) |
| `{{añoLetra}}` | Año (letras) |
| `{{alcalde}}` | Nombre del alcalde |
| `{{verificador}}` | Nombre del verificador |
| `{{numeroRadico}}` | Número de radicado |
| `{{fechaFirma}}` | Fecha de firma |
| `{{observacion}}` | Observaciones |

## Solución de Problemas

### Error: "Gotenberg error: connection refused"

**Causa:** Gotenberg no está corriendo.

**Solución:**
```bash
docker-compose up -d gotenberg
```

### Error: "docxtemplater-service error: connection refused"

**Causa:** El servicio Node.js no está corriendo.

**Solución:**
```bash
docker-compose up -d docxtemplater
```

### Error: "Archivo DOCX no recibido"

**Causa:** El nombre del campo multipart no coincide.

**Solución:** Verifica que el campo se llame `files` en la petición HTTP.

### PDF con formato diferente a la plantilla

**Causa:** Usando docx4j para conversión (NO recomendado).

**Solución:** Asegúrate de usar Gotenberg + docxtemplater, NO docx4j.

### PDF no respeta fuente Maven Pro

**Causa:** la imagen de Gotenberg no tenía la fuente instalada (o no se reconstruyó).

**Solución:**
```bash
docker-compose build --no-cache gotenberg
docker-compose up -d gotenberg
```

Verifica que exista al menos este archivo:
- `backend/src/main/resources/fonts/MavenPro[wght].ttf`

## Desarrollo Local

### Opción 1: Con Docker (Recomendado)

```bash
# Levantar solo Gotenberg y docxtemplater
docker-compose up -d gotenberg docxtemplater

# Ejecutar backend localmente
cd backend
mvn spring-boot:run
```

En perfil `dev` se permite localhost automáticamente con:

- `app.pdf.allow-localhost-services=true`
- `app.docxtemplater.url=http://localhost:3001/render-docx`
- `app.pdf.gotenberg.url=http://localhost:3000/forms/libreoffice/convert`

### Opción 2: Solo docxtemplater (sin Docker)

```bash
# Instalar dependencias Node.js
npm install

# Iniciar servicio docxtemplater
node docxtemplater-service.js

# Ejecutar backend localmente (necesitas Gotenberg en otro lado)
cd backend
mvn spring-boot:run
```

## Archivos Clave

| Archivo | Descripción |
|---------|-------------|
| `docxtemplater-service.js` | Servicio Node.js para procesar DOCX |
| `Dockerfile.docxtemplater` | Dockerfile para el servicio Node.js |
| `docker-compose.yml` | Orquestación de servicios |
| `DocumentoGeneradoService.java` | Servicio principal de generación PDF |
| `DocxtemplaterService.java` | Cliente Java para servicio Node.js |

## Notas Importantes

1. **NO usar docx4j para convertir a PDF** - Modifica el formato de la plantilla
2. **Gotenberg es REQUERIDO para producción** - Usa LibreOffice para conversión fiel
3. **docxtemplater preserva el formato** - Solo reemplaza texto, no modifica diseño
4. **Las plantillas deben usar {{ }}** - Sintaxis de docxtemplater, no `<< >>`
5. **Maven Pro debe estar en resources/fonts** - Si falta, LibreOffice hará fallback y cambia la apariencia.
