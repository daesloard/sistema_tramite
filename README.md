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
