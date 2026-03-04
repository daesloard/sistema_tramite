import HeaderConVolver from './HeaderConVolver';

const styles = {
  appVerificar: { minHeight: '100vh', background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' },
  appAdmin: { minHeight: '100vh', background: '#f5f5f5' },
  headerUsuario: { display: 'flex', alignItems: 'center', gap: '0.6rem', marginLeft: 'auto', flexWrap: 'wrap', justifyContent: 'flex-end' },
  btnLogout: {
    padding: '0.38rem 0.72rem',
    background: 'rgba(255,255,255,0.2)',
    color: '#fff',
    border: '2px solid #fff',
    borderRadius: '6px',
    cursor: 'pointer',
    fontSize: '0.78rem',
    fontWeight: 600,
    width: 'auto',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    whiteSpace: 'nowrap',
  },
  btnCambiarUsuario: {
    padding: '0.38rem 0.72rem',
    background: 'rgba(255,255,255,0.15)',
    color: '#fff',
    border: '2px solid #fff',
    borderRadius: '6px',
    cursor: 'pointer',
    fontSize: '0.78rem',
    fontWeight: 600,
    width: 'auto',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    whiteSpace: 'nowrap',
  },
  btnNotificaciones: {
    position: 'relative',
    padding: '0.38rem 0.72rem',
    background: 'rgba(255,255,255,0.2)',
    color: '#fff',
    border: '2px solid #fff',
    borderRadius: '6px',
    cursor: 'pointer',
    fontSize: '0.78rem',
    fontWeight: 700,
    width: 'auto',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    whiteSpace: 'nowrap',
  },
  badgeNotificaciones: {
    position: 'absolute',
    top: '-8px',
    right: '-8px',
    minWidth: '18px',
    height: '18px',
    padding: '0 4px',
    borderRadius: '999px',
    background: '#ef4444',
    color: '#fff',
    fontSize: '11px',
    fontWeight: 700,
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
};

export default function PanelConSesion({
  esAdmin,
  usuarioActual,
  etiquetaRol,
  titulo,
  noLeidasUsuario,
  onVolver,
  onAbrirCentroNotificaciones,
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
            <button style={styles.btnCambiarUsuario} onClick={onCambiarUsuario}>Cambiar usuario</button>
            <button style={styles.btnLogout} onClick={onCerrarSesion}>Cerrar Sesión</button>
          </div>
        )}
      />
      {children}
    </div>
  );
}
