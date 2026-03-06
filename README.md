# sistema_tramites

Aplicación web para radicar trámites en una entidad.

## Stack
- Backend: Java 21 + Spring Boot
- Frontend: React + Vite
- Base de datos (desarrollo): H2 en memoria

## Estructura
- `backend/`: API REST para CRUD de trámites
- `frontend/`: interfaz para radicar y consultar trámites

## Funcionalidad inicial
- Crear trámite (radicación)
- Listar trámites
- Consultar trámite por id
- Actualizar trámite
- Eliminar trámite

Entidad `Tramite`:
- `id`
- `numeroRadicado`
- `nombreSolicitante`
- `tipoTramite`
- `descripcion`
- `fechaRadicacion`
- `estado`

## Ejecutar en desarrollo

### 1) Backend
```powershell
cd backend
mvn spring-boot:run
```
Backend en `http://localhost:8080`

H2 Console: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:tramitesdb`
- User: `sa`
- Password: (vacío)

### 2) Frontend
```powershell
cd frontend
npm install
npm run dev
```
Frontend en `http://localhost:5173`

## Publicar frontend dentro del backend (SPA)

Para empaquetar el frontend React dentro de Spring Boot y habilitar rutas SPA en el mismo servicio:

```powershell
cd frontend
npm run build:backend
```

Esto hace dos pasos:
- Compila Vite (`frontend/dist`)
- Copia el resultado a `backend/src/main/resources/static`

Luego puedes iniciar backend normalmente:

```powershell
cd ../backend
mvn spring-boot:run
```

## Endpoints API
Base URL: `http://localhost:8080/api/tramites`

- `GET /api/tramites` → listar
- `GET /api/tramites/{id}` → obtener por id
- `POST /api/tramites` → crear
- `PUT /api/tramites/{id}` → actualizar
- `DELETE /api/tramites/{id}` → eliminar

## Estado
Proyecto base creado y listo para continuar con nuevas implementaciones (autenticación, archivos adjuntos, seguimiento de estados, notificaciones, etc.).

## Notificaciones Push (Windows, Android, iOS)

### Requisitos generales
- `https` en despliegue (o `localhost` en desarrollo)
- Service Worker activo (`frontend/public/sw-notificaciones.js`)
- Llaves VAPID configuradas en backend

### Configuración backend (variables de entorno)
```powershell
$env:APP_WEBPUSH_ENABLED="true"
$env:APP_WEBPUSH_PUBLIC_KEY="<TU_PUBLIC_KEY>"
$env:APP_WEBPUSH_PRIVATE_KEY="<TU_PRIVATE_KEY>"
$env:APP_WEBPUSH_SUBJECT="mailto:sistemas@cabuyaro-meta.gov.co"
```

### Flujo de activación en cliente
1. Iniciar sesión en panel.
2. Activar botón `Noti SO`.
3. Aceptar permiso del navegador.
4. El frontend registra suscripción push en backend.

### Prueba rápida de push
Endpoint autenticado:
- `POST /api/notificaciones/webpush/test`

Body opcional:
```json
{
	"titulo": "Prueba",
	"mensaje": "Push funcionando"
}
```

### Notas por plataforma
- Windows (Edge/Chrome): funciona directo en navegador con permiso concedido.
- Android (Chrome): funciona en navegador y mejora al instalar PWA.
- iOS (Safari): requiere iOS/iPadOS 16.4+ y usar la app instalada en pantalla de inicio para push estable.
