# Requerimiento de Componentes Visuales

## 1. Objetivo
Definir los componentes visuales obligatorios del sistema de trámites municipales para garantizar consistencia de interfaz, cobertura funcional por rol y validación de entregables frontend.

## 2. Alcance
Este requerimiento aplica al frontend completo del sistema, incluyendo vistas públicas y paneles operativos por rol:
- Público general
- Verificador
- Alcalde
- Administrador

## 3. Estructura de navegación (alto nivel)
El sistema debe incluir las siguientes vistas principales:
1. Inicio
2. Radicar solicitud
3. Verificar certificado (ciudadano)
4. Login
5. Panel de gestión (por rol)
   - Verificador
   - Alcalde
   - Administrador

## 4. Componentes visuales obligatorios por vista

### 4.1 Vista Inicio
**Componentes requeridos**
- Encabezado institucional con escudo y título del sistema.
- Bloque introductorio de bienvenida.
- Tarjetas de acceso rápido (Radicar, Verificar, Panel de gestión).
- Bloque de información importante.
- Pie de página institucional.

**Comportamiento esperado**
- Cada tarjeta debe enrutar a su flujo correspondiente.
- Diseño responsive para móvil y escritorio.

---

### 4.2 Vista Login
**Componentes requeridos**
- Tarjeta central de autenticación.
- Campos: usuario, contraseña.
- Botón de inicio de sesión con estado de carga.
- Área de mensajes de error.
- Mensaje de seguridad/autenticación.

**Comportamiento esperado**
- Validar campos obligatorios.
- Mostrar error legible ante credenciales inválidas.
- Persistir sesión al autenticarse.

---

### 4.3 Vista Radicar Solicitud
**Componentes requeridos**
- Formulario por pasos con barra de progreso.
- Secciones de datos personales y del trámite.
- Carga de documentos requeridos (solicitud, identidad, certificado soporte).
- Resumen previo al envío.
- Pantalla de confirmación con número de radicado y detalles.

**Comportamiento esperado**
- Validación de campos obligatorios y formatos.
- Validación de tamaño y tipo de archivo.
- Visualización del estado de carga de archivos.
- Confirmación final con mensaje de éxito.

---

### 4.4 Vista Verificar Certificado (Ciudadano)
**Componentes requeridos**
- Formulario de consulta por número de radicado/código.
- Segundo factor de validación aleatorio.
- Bloque de resultado (estado del trámite/certificado).
- Alertas de vigencia y estado.
- Bloque de solicitudes resueltas del solicitante.
- Acción para abrir certificado cuando aplique.

**Comportamiento esperado**
- Mensajería clara para encontrado/no encontrado/error.
- Mostrar estados diferenciados (radicado, validación, firma, finalizado, rechazado).

---

### 4.5 Panel Verificador
**Componentes requeridos**
- Encabezado de panel.
- Filtros por estado (pendientes, aprobadas, negadas).
- Lista de solicitudes y panel de detalle.
- Sección de documentos adjuntos con acciones Ver/Descargar.
- Indicador de días hábiles restantes (pendientes) y vencimiento.
- Campo de consecutivo y observaciones.
- Acciones: Aprobar, Rechazar, Notificar Administrador.
- Bloque de certificados generados con filtros.
- Acción de descarga de consolidado Excel.

**Comportamiento esperado**
- Mantener selección de solicitud activa mientras se navega en el panel.
- Bloquear aprobación/rechazo si faltan documentos obligatorios.
- Mostrar advertencias de inconsistencias documentales.

---

### 4.6 Panel Alcalde
**Componentes requeridos**
- Encabezado de panel.
- Filtros por estado (pendientes/aprobadas/negadas).
- Lista de solicitudes y panel de detalle.
- Vista previa de documento (texto/HTML/PDF según disponibilidad).
- Campo de firma digital (contraseña).
- Acción principal: Firmar certificado.
- Bloque de certificados generados con filtros.

**Comportamiento esperado**
- Permitir firma solo en estado EN_FIRMA.
- Refrescar lista al completar firma.

---

### 4.7 Panel Administrador
**Componentes requeridos**
- Encabezado de panel administrativo.
- Tabla de solicitudes radicadas.
- Expansión de detalle por trámite.
- Gestión de usuarios operativos (edición de nombre/usuario/correo).
- Bloque documental del trámite (estado, ver/descargar, carga faltantes).
- Estado de certificado final (disponibilidad y almacenamiento).
- Acción de notificar verificador.
- Bloque de trazabilidad/auditoría del trámite.

**Comportamiento esperado**
- Refresco periódico de datos sin perder el ítem seleccionado.
- Conservación del trámite expandido tras actualización automática.
- Visualización en tiempo real de eventos de auditoría tras acciones.

## 5. Estados visuales transversales (obligatorios)
Para todas las vistas y componentes con datos remotos:
- Estado de carga.
- Estado vacío (sin resultados/sin registros).
- Estado error (mensaje claro, no técnico para usuario final).
- Estado éxito (confirmación de acciones).
- Estados deshabilitado/procesando en botones de acción.

## 6. Requisitos de experiencia de usuario
- Diseño responsive para móvil y escritorio.
- Jerarquía visual clara (encabezado, lista, detalle, acciones).
- Consistencia de estilos de botones, badges, alertas y tarjetas.
- Mensajería en español, clara y orientada a acción.

## 7. Criterios de aceptación
1. El sistema muestra todas las vistas definidas en este documento.
2. Cada vista contiene sus componentes visuales obligatorios.
3. Los flujos por rol presentan únicamente acciones permitidas para su perfil.
4. El panel administrador mantiene selección/expansión durante refrescos periódicos.
5. El panel verificador muestra el contador de días hábiles restantes en solicitudes pendientes.
6. Se visualizan correctamente estados de carga, vacío, error y éxito.
7. La interfaz es usable en móvil y escritorio sin pérdida de funcionalidad crítica.

## 8. Artefactos de referencia en el código
- `frontend/src/App.jsx`
- `frontend/src/components/Login.jsx`
- `frontend/src/components/FormularioCertificado.jsx`
- `frontend/src/components/VerificadorCertificado.jsx`
- `frontend/src/components/PanelVerificador.jsx`
- `frontend/src/components/PanelAlcalde.jsx`
