import { getVistaInicioStyles } from '../styles/components/VistaInicioStyles';
const OPCIONES_INICIO = [
  { key: 'radicar', icono: '📝', titulo: 'Radicar Solicitud', descripcion: 'Solicita tu certificado de residencia en línea', boton: 'Empezar', color: '#4CAF50' },
  { key: 'verificar', icono: '🔍', titulo: 'Verificar Certificado', descripcion: 'Consulta el estado de tu solicitud', boton: 'Verificar', color: '#2196F3' },
  { key: 'panel', icono: '👥', titulo: 'Panel de Gestión', descripcion: 'Acceso para verificador, alcalde y administrador', boton: 'Acceder', color: '#9C27B0' },
];

const styles = getVistaInicioStyles();

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