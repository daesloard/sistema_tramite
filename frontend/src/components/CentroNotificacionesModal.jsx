import { getCentroNotificacionesModalStyles } from '../styles/components/CentroNotificacionesModalStyles';
const styles = getCentroNotificacionesModalStyles();

export default function CentroNotificacionesModal({
  abierto,
  cargando,
  notificaciones,
  onCerrar,
  onMarcarTodas,
  onMarcarUna,
  formatearFechaHora,
}) {
  if (!abierto) return null;

  return (
    <div style={styles.notiOverlay} onClick={onCerrar}>
      <div style={styles.notiModal} onClick={(e) => e.stopPropagation()}>
        <div style={styles.notiHeader}>
          <h3 style={styles.notiTitle}>Centro de notificaciones</h3>
          <div style={styles.notiActions}>
            <button style={styles.notiBtnSmall} onClick={onMarcarTodas}>Marcar todas leídas</button>
            <button style={styles.notiBtnClose} onClick={onCerrar}>Cerrar</button>
          </div>
        </div>
        <div style={styles.notiBody}>
          {cargando ? <p style={styles.notiEmpty}>Cargando notificaciones...</p> : null}
          {!cargando && notificaciones.length === 0 ? <p style={styles.notiEmpty}>No tienes notificaciones.</p> : null}
          {!cargando && notificaciones.map((notificacion) => (
            <div
              key={notificacion.id}
              style={{ ...styles.notiItem, ...(notificacion.leida ? {} : styles.notiItemNoLeida) }}
            >
              <p style={styles.notiItemTitulo}>{notificacion.titulo || 'Notificación'}</p>
              <p style={styles.notiItemMensaje}>{notificacion.mensaje || '-'}</p>
              <p style={styles.notiItemMeta}>{formatearFechaHora(notificacion.fechaCreacion)}</p>
              {!notificacion.leida ? (
                <div style={styles.notiItemBtns}>
                  <button style={styles.notiBtnSmall} onClick={() => onMarcarUna(notificacion.id)}>Marcar leída</button>
                </div>
              ) : null}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}