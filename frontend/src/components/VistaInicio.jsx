const OPCIONES_INICIO = [
  { key: 'radicar', icono: '📝', titulo: 'Radicar Solicitud', descripcion: 'Solicita tu certificado de residencia en línea', boton: 'Empezar', color: '#4CAF50' },
  { key: 'verificar', icono: '🔍', titulo: 'Verificar Certificado', descripcion: 'Consulta el estado de tu solicitud', boton: 'Verificar', color: '#2196F3' },
  { key: 'panel', icono: '👥', titulo: 'Panel de Gestión', descripcion: 'Acceso para verificador, alcalde y administrador', boton: 'Acceder', color: '#9C27B0' },
];

const styles = {
  appInicio: {
    minHeight: '100vh',
    display: 'flex',
    flexDirection: 'column',
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  },
  headerInicio: {
    background: 'rgba(0,0,0,0.1)',
    color: '#fff',
    padding: 'clamp(1.5rem, 5vw, 3rem) clamp(1rem, 4vw, 2rem)',
    textAlign: 'center',
  },
  headerMarcaInicio: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '0.85rem',
  },
  headerTextoInicio: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'flex-start',
    textAlign: 'left',
    gap: '0.2rem',
  },
  headerEscudoInicio: {
    width: 'clamp(200px, 30vw, 250px)',
    height: 'auto',
    filter: 'drop-shadow(0 4px 10px rgba(0,0,0,0.25))',
  },
  headerTitle: { fontSize: 'clamp(1.6rem, 6vw, 2.5rem)', margin: 0, fontWeight: 700 },
  headerSubtitle: { fontSize: 'clamp(0.95rem, 3.2vw, 1.1rem)', opacity: 0.9, margin: 0 },
  inicioContenedor: { flex: 1, padding: 'clamp(1rem, 4vw, 3rem) clamp(0.9rem, 4vw, 2rem)', maxWidth: '1200px', margin: '0 auto', width: '100%' },
  intro: { textAlign: 'center', color: '#fff', marginBottom: '3rem' },
  introTitle: { fontSize: 'clamp(1.35rem, 5vw, 2rem)', marginBottom: '0.5rem' },
  introText: { fontSize: 'clamp(0.95rem, 3vw, 1.1rem)', opacity: 0.95, margin: 0 },
  opciones: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1rem', marginBottom: '2rem' },
  opcionCard: {
    background: '#fff',
    borderRadius: '12px',
    padding: 'clamp(1rem, 4vw, 2rem)',
    textAlign: 'center',
    cursor: 'pointer',
    boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
    border: '2px solid transparent',
  },
  opcionIcono: { fontSize: 'clamp(2rem, 8vw, 3rem)', marginBottom: '1rem' },
  opcionTitulo: { fontSize: 'clamp(1.05rem, 4.3vw, 1.3rem)', color: '#333', marginBottom: '0.5rem' },
  opcionDesc: { color: '#666', marginBottom: '1.5rem', fontSize: '0.95rem' },
  opcionBoton: { padding: '0.75rem 2rem', border: 'none', borderRadius: '6px', fontWeight: 600, color: '#fff', cursor: 'pointer' },
  infoAdicional: { background: 'rgba(255,255,255,0.95)', padding: 'clamp(1rem, 4vw, 2rem)', borderRadius: '12px', marginBottom: '2rem' },
  infoTitle: { color: '#333', marginBottom: '1rem', textAlign: 'center' },
  infoList: { listStyle: 'none', display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '0.75rem', margin: 0, padding: 0 },
  infoItem: { color: '#555', padding: '0.5rem 0' },
  footerInicio: { background: 'rgba(0,0,0,0.2)', color: '#fff', textAlign: 'center', padding: '1.5rem', marginTop: 'auto' },
};

function TarjetaInicio({ opcion, onClick }) {
  return (
    <div style={{ ...styles.opcionCard, borderColor: `${opcion.color}33` }} onClick={onClick}>
      <div style={styles.opcionIcono}>{opcion.icono}</div>
      <h3 style={styles.opcionTitulo}>{opcion.titulo}</h3>
      <p style={styles.opcionDesc}>{opcion.descripcion}</p>
      <button style={{ ...styles.opcionBoton, background: opcion.color }}>{opcion.boton}</button>
    </div>
  );
}

export default function VistaInicio({ onAbrirVista }) {
  return (
    <div style={styles.appInicio}>
      <header style={styles.headerInicio}>
        <div style={styles.headerMarcaInicio}>
          <img src="/escudo.png" alt="Escudo del municipio" style={styles.headerEscudoInicio} />
          <div style={styles.headerTextoInicio}>
            <h1 style={styles.headerTitle}>Ventanilla Unica Virtual</h1>
            <p style={styles.headerSubtitle}>Sistema Municipal de Trámites</p>
          </div>
        </div>
      </header>

      <main style={styles.inicioContenedor}>
        <div style={styles.intro}>
          <h2 style={styles.introTitle}>Bienvenido a la Ventanilla Virtual Municipal</h2>
          <p style={styles.introText}>Realiza tus trámites de forma rápida y segura desde cualquier lugar</p>
        </div>

        <div style={styles.opciones}>
          {OPCIONES_INICIO.map((opcion) => (
            <TarjetaInicio key={opcion.key} opcion={opcion} onClick={() => onAbrirVista(opcion.key)} />
          ))}
        </div>

        <div style={styles.infoAdicional}>
          <h3 style={styles.infoTitle}>Información Importante</h3>
          <ul style={styles.infoList}>
            <li style={styles.infoItem}>✓ El certificado tendrá vigencia de 6 meses</li>
            <li style={styles.infoItem}>✓ Tiempo de procesamiento: máximo 10 días hábiles</li>
            <li style={styles.infoItem}>✓ Recibirás confirmación por correo electrónico</li>
            <li style={styles.infoItem}>✓ También estará disponible para impresión en ventanilla</li>
          </ul>
        </div>
      </main>

      <footer style={styles.footerInicio}>
        <p>&copy; 2026 Municipio de Cabuyaro (Meta). Todos los derechos reservados.</p>
      </footer>
    </div>
  );
}
