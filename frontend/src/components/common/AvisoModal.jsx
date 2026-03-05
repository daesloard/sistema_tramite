import { getAvisoModalStyles } from '../../styles/components/AvisoModalStyles';
const styles = getAvisoModalStyles();

const toneStyles = {
  success: { background: '#e8f5e9', borderColor: '#81c784', color: '#1b5e20' },
  warning: { background: '#fff4e5', borderColor: '#f59e0b', color: '#92400e' },
  info: { background: '#e3f2fd', borderColor: '#64b5f6', color: '#0d47a1' },
  error: { background: '#ffebee', borderColor: '#ef5350', color: '#b71c1c' },
};

export default function AvisoModal({ aviso, onClose }) {
  if (!aviso) return null;

  const tone = toneStyles[aviso.tipo] || toneStyles.error;

  return (
    <div style={styles.overlay} onClick={onClose}>
      <div style={{ ...styles.panel, ...tone }} onClick={(e) => e.stopPropagation()}>
        <p style={styles.text}>{aviso.mensaje}</p>
        <div style={styles.actions}>
          <button style={styles.close} onClick={onClose} aria-label="Cerrar aviso">
            Entendido
          </button>
        </div>
      </div>
    </div>
  );
}