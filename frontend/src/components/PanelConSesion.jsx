import HeaderConVolver from './HeaderConVolver';

import { getPanelConSesionStyles } from '../styles/components/PanelConSesionStyles';
const styles = getPanelConSesionStyles();

export default function PanelConSesion({
  esAdmin,
  usuarioActual,
  etiquetaRol,
  titulo,
  noLeidasUsuario,
  soportaNotificacionesNavegador,
  notificacionesNavegadorHabilitadas,
  onVolver,
  onAbrirCentroNotificaciones,
  onToggleNotificacionesNavegador,
  onCambiarUsuario,
  onCerrarSesion,
  children,
}) {
  return (
    <div style={esAdmin ? styles.appAdmin : styles.appVerificar}>
      <HeaderConVolver
        onVolver={onVolver}
        titulo={titulo}
        derecha={(
          <div style={styles.headerUsuario}>
            <span>{etiquetaRol} {usuarioActual?.nombreCompleto}</span>
            <button style={styles.btnNotificaciones} onClick={onAbrirCentroNotificaciones}>
              🔔
              {noLeidasUsuario > 0 ? <span style={styles.badgeNotificaciones}>{noLeidasUsuario > 99 ? '99+' : noLeidasUsuario}</span> : null}
            </button>
            {soportaNotificacionesNavegador ? (
              <button
                style={{
                  ...styles.btnNotificacionesBrowser,
                  ...(notificacionesNavegadorHabilitadas ? styles.btnNotificacionesBrowserActiva : {}),
                }}
                onClick={onToggleNotificacionesNavegador}
                title={notificacionesNavegadorHabilitadas ? 'Desactivar notificaciones del navegador' : 'Activar notificaciones del navegador'}
              >
                {notificacionesNavegadorHabilitadas ? '🟢 Noti SO' : '⚪ Noti SO'}
              </button>
            ) : null}
            <button style={styles.btnCambiarUsuario} onClick={onCambiarUsuario}>Cambiar usuario</button>
            <button style={styles.btnLogout} onClick={onCerrarSesion}>Cerrar Sesión</button>
          </div>
        )}
      />
      {children}
    </div>
  );
}