const styles = {
  notiOverlay: {
    position: 'fixed',
    inset: 0,
    background: 'rgba(17, 24, 39, 0.35)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '16px',
    zIndex: 130,
  },
  notiModal: {
    width: 'min(95vw, 640px)',
    maxHeight: '80vh',
    overflow: 'hidden',
    background: '#fff',
    borderRadius: '12px',
    boxShadow: '0 15px 40px rgba(0,0,0,0.25)',
    border: '1px solid #e5e7eb',
    display: 'flex',
    flexDirection: 'column',
  },
  notiHeader: { padding: '12px 14px', borderBottom: '1px solid #e5e7eb', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' },
  notiTitle: { margin: 0, fontSize: '15px', color: '#111827' },
  notiActions: { display: 'flex', gap: '8px', alignItems: 'center' },
  notiBtnSmall: { border: 'none', background: '#2563eb', color: '#fff', borderRadius: '6px', padding: '6px 10px', fontSize: '12px', fontWeight: 600, cursor: 'pointer' },
  notiBtnClose: { border: 'none', background: '#1f2937', color: '#fff', borderRadius: '6px', padding: '6px 10px', fontSize: '12px', fontWeight: 600, cursor: 'pointer' },
  notiBody: { padding: '10px', overflowY: 'auto', display: 'grid', gap: '8px' },
  notiItem: { border: '1px solid #e5e7eb', borderRadius: '8px', padding: '10px', background: '#f9fafb' },
  notiItemNoLeida: { borderColor: '#93c5fd', background: '#eff6ff' },
  notiItemTitulo: { margin: '0 0 4px 0', fontSize: '13px', fontWeight: 700, color: '#111827' },
  notiItemMensaje: { margin: '0 0 6px 0', fontSize: '12px', color: '#374151' },
  notiItemMeta: { margin: 0, fontSize: '11px', color: '#6b7280' },
  notiItemBtns: { marginTop: '8px', display: 'flex', justifyContent: 'flex-end' },
  notiEmpty: { margin: 0, padding: '16px', textAlign: 'center', color: '#6b7280', fontSize: '13px' },
};

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
