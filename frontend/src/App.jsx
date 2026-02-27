import { Fragment, useCallback, useEffect, useState } from 'react';
import { listarTramites } from './services/api';
import { API_AUTH_URL } from './config/api';
import FormularioCertificado from './components/FormularioCertificado';
import VerificadorCertificado from './components/VerificadorCertificado';
import PanelVerificador from './components/PanelVerificador';
import PanelAlcalde from './components/PanelAlcalde';
import Login from './components/Login';

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
  headerEscudoPanel: {
    width: 'clamp(110px, 24vw, 250px)',
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

  appRadicar: { minHeight: '100vh', background: '#f5f5f5' },
  appVerificar: { minHeight: '100vh', background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' },
  appAdmin: { minHeight: '100vh', background: '#f5f5f5' },

  headerComun: {
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    color: '#fff',
    padding: 'clamp(0.9rem, 3.5vw, 1.5rem) clamp(0.9rem, 4vw, 2rem)',
    display: 'grid',
    gridTemplateColumns: 'auto 1fr auto',
    alignItems: 'center',
    gap: '0.9rem',
  },
  headerCentro: { display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.7rem', minWidth: 0 },
  headerTitleCommon: { margin: 0, fontSize: 'clamp(1rem, 4.5vw, 1.5rem)', textAlign: 'center' },
  headerDerecha: { justifySelf: 'end' },
  btnVolver: {
    padding: '0.45rem 0.9rem',
    background: 'rgba(255,255,255,0.2)',
    color: '#fff',
    border: '2px solid #fff',
    borderRadius: '6px',
    cursor: 'pointer',
    fontWeight: 600,
    fontSize: '0.82rem',
    width: 'auto',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    whiteSpace: 'nowrap',
  },
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

  adminContenedor: { maxWidth: '1200px', margin: '0 auto', padding: 'clamp(0.9rem, 4vw, 2rem)' },
  adminCard: { background: '#fff', borderRadius: '8px', padding: 'clamp(1rem, 4vw, 2rem)', boxShadow: '0 2px 8px rgba(0,0,0,0.1)' },
  adminCardTitle: { color: '#333', marginBottom: '1.5rem' },
  adminDetalle: { border: '1px solid #e5e7eb', borderRadius: '8px', background: '#f9fafb', padding: '0.8rem' },
  adminDetalleTitle: { margin: '0 0 0.8rem 0', color: '#1f2937', fontSize: '1rem' },
  adminDetalleGrid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '0.6rem 1rem' },
  adminDetalleItem: { margin: 0, color: '#374151', fontSize: '0.92rem' },
  adminDetalleLabel: { fontWeight: 700, color: '#111827' },
  tablaWrapper: { overflowX: 'auto', overflowY: 'auto', maxHeight: '420px' },
  tabla: { width: '100%', borderCollapse: 'collapse' },
  th: { padding: '0.75rem', textAlign: 'left', fontWeight: 600, color: '#333', borderBottom: '2px solid #ddd', background: 'linear-gradient(135deg, #667eea15, #764ba215)' },
  td: { padding: '0.75rem', borderBottom: '1px solid #eee', color: '#666' },
  celdaRadicado: { fontFamily: 'Courier New, monospace', fontWeight: 'bold', color: '#667eea', letterSpacing: '0.5px' },
  badge: { display: 'inline-block', padding: '0.4rem 0.8rem', borderRadius: '12px', fontSize: '0.85rem', fontWeight: 600 },
  btnVer: { padding: '0.5rem 1rem', background: '#667eea', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 600 },
  usuariosCard: { background: '#fff', borderRadius: '8px', padding: 'clamp(1rem, 4vw, 2rem)', boxShadow: '0 2px 8px rgba(0,0,0,0.1)', marginBottom: '1rem' },
  usuariosTitulo: { color: '#333', marginBottom: '1rem' },
  usuariosGrid: { display: 'grid', gap: '0.75rem' },
  usuarioItem: { border: '1px solid #e5e7eb', borderRadius: '8px', padding: '0.75rem', background: '#fafafa' },
  usuarioFila: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.6rem', alignItems: 'end' },
  usuarioLabel: { display: 'block', fontSize: '12px', color: '#6b7280', marginBottom: '4px', fontWeight: 600 },
  usuarioInput: { width: '100%', padding: '8px 10px', border: '1px solid #cfd8dc', borderRadius: '6px', fontSize: '14px', boxSizing: 'border-box' },
  usuarioMeta: { fontSize: '12px', color: '#4b5563', marginTop: '6px' },
  btnGuardarUsuario: { padding: '8px 12px', border: 'none', borderRadius: '6px', background: '#2563eb', color: '#fff', cursor: 'pointer', fontWeight: 600 },
  btnGuardarUsuarioDisabled: { background: '#9ca3af', cursor: 'not-allowed' },
  seccionHeader: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '0.8rem', marginBottom: '1rem', flexWrap: 'wrap' },
  btnToggleSeccion: { padding: '0.35rem 0.75rem', background: '#4f46e5', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 600, fontSize: '0.78rem', width: 'auto', whiteSpace: 'nowrap' },
  filtrosCert: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '8px', marginBottom: '10px' },
  inputFiltro: { width: '100%', border: '1px solid #cfd8dc', borderRadius: '6px', padding: '8px 10px', fontSize: '13px', boxSizing: 'border-box' },
  listaCertificados: { display: 'grid', gap: '8px', maxHeight: '220px', overflowY: 'auto' },
  certItem: { border: '1px solid #e5e7eb', borderRadius: '8px', padding: '8px 10px', background: '#f9fafb' },
  certMeta: { margin: '2px 0', fontSize: '12px', color: '#4b5563' },
};

const getEstadoBadgeStyle = (estado) => {
  const key = (estado || '').toLowerCase();
  if (key === 'radicado') return { background: '#e3f2fd', color: '#1976d2' };
  if (key === 'en_validacion') return { background: '#fff3e0', color: '#f57c00' };
  if (key === 'en_firma') return { background: '#f3e5f5', color: '#7b1fa2' };
  if (key === 'finalizado') return { background: '#e8f5e9', color: '#388e3c' };
  return { background: '#ffebee', color: '#d32f2f' };
};

function HeaderConVolver({ onVolver, titulo, derecha }) {
  const [esMovil, setEsMovil] = useState(() => {
    if (typeof window === 'undefined') return false;
    return window.innerWidth <= 768;
  });

  useEffect(() => {
    const handleResize = () => {
      setEsMovil(window.innerWidth <= 768);
    };

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  const headerStyle = esMovil
    ? { ...styles.headerComun, gridTemplateColumns: '1fr', justifyItems: 'center', rowGap: '0.65rem' }
    : styles.headerComun;

  const headerCentroStyle = esMovil
    ? { ...styles.headerCentro, flexDirection: 'column', gap: '0.4rem' }
    : styles.headerCentro;

  const headerDerechaStyle = esMovil
    ? { ...styles.headerDerecha, justifySelf: 'center' }
    : styles.headerDerecha;

  return (
    <header style={headerStyle}>
      <button style={styles.btnVolver} onClick={onVolver}>← Volver al Inicio</button>
      <div style={headerCentroStyle}>
        <img src="/escudo.png" alt="Escudo del municipio" style={styles.headerEscudoPanel} />
        {titulo ? <h1 style={styles.headerTitleCommon}>{titulo}</h1> : null}
      </div>
      <div style={headerDerechaStyle}>{derecha || null}</div>
    </header>
  );
}

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

export default function App() {
  const [vista, setVista] = useState('inicio');
  const [tramites, setTramites] = useState([]);
  const [loading, setLoading] = useState(false);
  const [usuariosOperativos, setUsuariosOperativos] = useState([]);
  const [usuariosExpandido, setUsuariosExpandido] = useState(false);
  const [loadingUsuarios, setLoadingUsuarios] = useState(false);
  const [guardandoUsuarioId, setGuardandoUsuarioId] = useState(null);
  const [tramiteExpandidoId, setTramiteExpandidoId] = useState(null);
  const [filtroCertRadicado, setFiltroCertRadicado] = useState('');
  const [filtroCertNombre, setFiltroCertNombre] = useState('');
  const [filtroCertTipo, setFiltroCertTipo] = useState('todos');
  const [certificadosExpandido, setCertificadosExpandido] = useState(false);
  const [usuarioActual, setUsuarioActual] = useState(null);
  const [vistaLoginDestino, setVistaLoginDestino] = useState(null);

  const cargarTramites = useCallback(async () => {
    setLoading(true);
    try {
      const data = await listarTramites();
      setTramites(data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, []);

  const cargarUsuariosOperativos = useCallback(async () => {
    if (!usuarioActual?.username) return;
    setLoadingUsuarios(true);
    try {
      const response = await fetch(`${API_AUTH_URL}/usuarios-operativos`, {
        headers: { 'X-Admin-Username': usuarioActual.username },
      });
      if (!response.ok) {
        const msg = await response.text();
        throw new Error(msg || 'No se pudo cargar usuarios operativos');
      }
      const data = await response.json();
      setUsuariosOperativos(data.map((u) => ({
        ...u,
        editNombre: u.nombreCompleto || '',
        editUsername: u.username || '',
        editEmail: u.email || '',
      })));
    } catch (err) {
      console.error(err);
      alert(`❌ ${err.message}`);
    } finally {
      setLoadingUsuarios(false);
    }
  }, [usuarioActual?.username]);

  useEffect(() => {
    if (vista === 'panel' && usuarioActual?.rol === 'ADMINISTRADOR') {
      cargarTramites();
      cargarUsuariosOperativos();
    }
  }, [vista, usuarioActual, cargarTramites, cargarUsuariosOperativos]);

  const actualizarCampoUsuario = (id, campo, valor) => {
    setUsuariosOperativos((prev) => prev.map((u) => (u.id === id ? { ...u, [campo]: valor } : u)));
  };

  const guardarUsuarioOperativo = async (usuario) => {
    if (!usuarioActual?.username) return;
    const nombre = (usuario.editNombre || '').trim();
    const username = (usuario.editUsername || '').trim();
    const email = (usuario.editEmail || '').trim();
    if (!nombre) return alert('El nombre completo es obligatorio');
    if (!username) return alert('El nombre de usuario es obligatorio');
    if (!email) return alert('El correo es obligatorio');

    setGuardandoUsuarioId(usuario.id);
    try {
      const response = await fetch(`${API_AUTH_URL}/usuarios-operativos/${usuario.id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'X-Admin-Username': usuarioActual.username,
        },
        body: JSON.stringify({ nombreCompleto: nombre, username, email }),
      });
      if (!response.ok) {
        const msg = await response.text();
        throw new Error(msg || 'No se pudo actualizar el usuario');
      }

      setUsuariosOperativos((prev) => prev.map((u) => (
        u.id === usuario.id
          ? {
              ...u,
              nombreCompleto: nombre,
              username,
              email,
              editNombre: nombre,
              editUsername: username,
              editEmail: email,
            }
          : u
      )));
      alert('✅ Usuario actualizado');
    } catch (err) {
      alert(`❌ ${err.message}`);
    } finally {
      setGuardandoUsuarioId(null);
    }
  };

  const cerrarSesion = () => {
    setUsuarioActual(null);
    setVistaLoginDestino(null);
    localStorage.removeItem('token');
    localStorage.removeItem('usuario');
    localStorage.removeItem('user');
    setVista('inicio');
  };

  const cambiarUsuario = () => {
    if (esVistaProtegida(vista)) {
      setVistaLoginDestino(vista);
    }
    setUsuarioActual(null);
    localStorage.removeItem('token');
    localStorage.removeItem('usuario');
    localStorage.removeItem('user');
    setVista('login');
  };

  const esVistaProtegida = (vistaDestino) => vistaDestino === 'panel';

  const abrirVista = (vistaDestino) => {
    if (!esVistaProtegida(vistaDestino)) {
      setVista(vistaDestino);
      return;
    }

    if (usuarioActual) {
      setVista(vistaDestino);
      return;
    }

    setVistaLoginDestino(vistaDestino);
    setVista('login');
  };

  const handleLoginUnificado = (usuario) => {
    const destino = vistaLoginDestino || (esVistaProtegida(vista) ? vista : 'panel');

    setUsuarioActual(usuario);
    setVista(destino);
  };

  const formatoFecha = (valor) => {
    if (!valor) return '-';
    const fecha = new Date(valor);
    if (Number.isNaN(fecha.getTime())) return '-';
    return fecha.toLocaleDateString('es-CO');
  };

  const certificadosGeneradosFiltrados = tramites
    .filter((t) => t.estado === 'FINALIZADO' || t.estado === 'RECHAZADO')
    .filter((t) => {
      const matchRadicado = (t.numeroRadicado || '').toLowerCase().includes(filtroCertRadicado.toLowerCase().trim());
      const matchNombre = (t.nombreSolicitante || '').toLowerCase().includes(filtroCertNombre.toLowerCase().trim());
      const matchTipo = filtroCertTipo === 'todos'
        || (filtroCertTipo === 'positiva' && t.estado === 'FINALIZADO')
        || (filtroCertTipo === 'negativa' && t.estado === 'RECHAZADO');
      return matchRadicado && matchNombre && matchTipo;
    });

  if (vista === 'inicio') {
    return (
      <div style={styles.appInicio}>
        <header style={styles.headerInicio}>
          <div style={styles.headerMarcaInicio}>
            <img src="/escudo.png" alt="Escudo del municipio" style={styles.headerEscudoInicio} />
            <div style={styles.headerTextoInicio}>
              <h1 style={styles.headerTitle}>Ventanilla Virtual</h1>
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
              <TarjetaInicio key={opcion.key} opcion={opcion} onClick={() => abrirVista(opcion.key)} />
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

  if (vista === 'radicar') {
    return (
      <div style={styles.appRadicar}>
        <HeaderConVolver onVolver={() => setVista('inicio')} titulo="Radicar Solicitud" />
        <FormularioCertificado />
      </div>
    );
  }

  if (vista === 'verificar') {
    return (
      <div style={styles.appVerificar}>
        <HeaderConVolver onVolver={() => setVista('inicio')} />
        <VerificadorCertificado />
      </div>
    );
  }

  if (vista === 'login') {
    return (
      <div style={styles.appVerificar}>
        <HeaderConVolver onVolver={() => setVista('inicio')} />
        <Login onLoginSuccess={handleLoginUnificado} />
      </div>
    );
  }

  if (vista === 'panel') {
    if (!usuarioActual) {
      return (
        <div style={styles.appVerificar}>
          <HeaderConVolver onVolver={() => setVista('inicio')} />
          <Login onLoginSuccess={handleLoginUnificado} />
        </div>
      );
    }

    if (usuarioActual.rol === 'VERIFICADOR') {
      return (
        <div style={styles.appVerificar}>
          <HeaderConVolver
            onVolver={() => setVista('inicio')}
            derecha={(
              <div style={styles.headerUsuario}>
                <span>👤 {usuarioActual.nombreCompleto} (Verificador)</span>
                <button style={styles.btnCambiarUsuario} onClick={cambiarUsuario}>Cambiar usuario</button>
                <button style={styles.btnLogout} onClick={cerrarSesion}>Cerrar Sesión</button>
              </div>
            )}
          />
          <PanelVerificador />
        </div>
      );
    }

    if (usuarioActual.rol === 'ALCALDE') {
      return (
        <div style={styles.appVerificar}>
          <HeaderConVolver
            onVolver={() => setVista('inicio')}
            derecha={(
              <div style={styles.headerUsuario}>
                <span>👨‍⚖️ {usuarioActual.nombreCompleto} (Alcalde)</span>
                <button style={styles.btnCambiarUsuario} onClick={cambiarUsuario}>Cambiar usuario</button>
                <button style={styles.btnLogout} onClick={cerrarSesion}>Cerrar Sesión</button>
              </div>
            )}
          />
          <PanelAlcalde />
        </div>
      );
    }

    return (
      <div style={styles.appAdmin}>
        <HeaderConVolver
          onVolver={() => setVista('inicio')}
          titulo="Panel Administrativo"
          derecha={(
            <div style={styles.headerUsuario}>
              <span>⚙️ {usuarioActual.nombreCompleto} (Admin)</span>
              <button style={styles.btnCambiarUsuario} onClick={cambiarUsuario}>Cambiar usuario</button>
              <button style={styles.btnLogout} onClick={cerrarSesion}>Cerrar Sesión</button>
            </div>
          )}
        />

        <main style={styles.adminContenedor}>
          <div style={styles.usuariosCard}>
            <div style={styles.seccionHeader}>
              <h2 style={{ ...styles.usuariosTitulo, marginBottom: 0 }}>Usuarios Operativos</h2>
              <button
                style={styles.btnToggleSeccion}
                onClick={() => setUsuariosExpandido((prev) => !prev)}
              >
                {usuariosExpandido ? 'Ocultar' : 'Mostrar'}
              </button>
            </div>

            {usuariosExpandido && loadingUsuarios ? <p>Cargando usuarios...</p> : null}
            {usuariosExpandido && !loadingUsuarios && usuariosOperativos.length === 0 ? <p>No hay usuarios operativos disponibles.</p> : null}
            {usuariosExpandido && !loadingUsuarios && usuariosOperativos.length > 0 ? (
              <div style={styles.usuariosGrid}>
                {usuariosOperativos.map((usuario) => (
                  <div key={usuario.id} style={styles.usuarioItem}>
                    <div style={styles.usuarioFila}>
                      <div>
                        <label style={styles.usuarioLabel}>Rol</label>
                        <input style={styles.usuarioInput} value={usuario.rol} readOnly />
                      </div>
                      <div>
                        <label style={styles.usuarioLabel}>Nombre Completo</label>
                        <input
                          style={styles.usuarioInput}
                          value={usuario.editNombre}
                          onChange={(e) => actualizarCampoUsuario(usuario.id, 'editNombre', e.target.value)}
                        />
                      </div>
                      <div>
                        <label style={styles.usuarioLabel}>Username</label>
                        <input
                          style={styles.usuarioInput}
                          value={usuario.editUsername}
                          onChange={(e) => actualizarCampoUsuario(usuario.id, 'editUsername', e.target.value)}
                        />
                      </div>
                      <div>
                        <label style={styles.usuarioLabel}>Correo</label>
                        <input
                          style={styles.usuarioInput}
                          type="email"
                          value={usuario.editEmail}
                          onChange={(e) => actualizarCampoUsuario(usuario.id, 'editEmail', e.target.value)}
                        />
                      </div>
                      <div>
                        <button
                          style={{ ...styles.btnGuardarUsuario, ...(guardandoUsuarioId === usuario.id ? styles.btnGuardarUsuarioDisabled : {}) }}
                          onClick={() => guardarUsuarioOperativo(usuario)}
                          disabled={guardandoUsuarioId === usuario.id}
                        >
                          {guardandoUsuarioId === usuario.id ? 'Guardando...' : 'Guardar'}
                        </button>
                      </div>
                    </div>
                    <div style={styles.usuarioMeta}>Estado: {usuario.activo ? 'Activo' : 'Inactivo'}</div>
                  </div>
                ))}
              </div>
            ) : null}
          </div>

          <div style={styles.adminCard}>
            <h2 style={styles.adminCardTitle}>Solicitudes Radicadas</h2>

            {loading && <p>Cargando...</p>}
            {!loading && tramites.length === 0 && <p>No hay solicitudes registradas.</p>}
            {!loading && tramites.length > 0 ? (
              <div style={styles.tablaWrapper}>
                <table style={styles.tabla}>
                  <thead>
                    <tr>
                      <th style={{ ...styles.th, position: 'sticky', top: 0, zIndex: 2 }}>Radicado</th>
                      <th style={{ ...styles.th, position: 'sticky', top: 0, zIndex: 2 }}>Solicitante</th>
                      <th style={{ ...styles.th, position: 'sticky', top: 0, zIndex: 2 }}>Tipo</th>
                      <th style={{ ...styles.th, position: 'sticky', top: 0, zIndex: 2 }}>Estado</th>
                      <th style={{ ...styles.th, position: 'sticky', top: 0, zIndex: 2 }}>Fecha Radicación</th>
                      <th style={{ ...styles.th, position: 'sticky', top: 0, zIndex: 2 }}>Vencimiento</th>
                      <th style={{ ...styles.th, position: 'sticky', top: 0, zIndex: 2 }}>Acciones</th>
                    </tr>
                  </thead>
                  <tbody>
                    {tramites.map((tramite) => {
                      const expandido = tramiteExpandidoId === tramite.id;
                      return (
                        <Fragment key={tramite.id}>
                          <tr>
                            <td style={{ ...styles.td, ...styles.celdaRadicado }}>{tramite.numeroRadicado}</td>
                            <td style={styles.td}>{tramite.nombreSolicitante}</td>
                            <td style={styles.td}>{tramite.tipoTramite}</td>
                            <td style={styles.td}>
                              <span style={{ ...styles.badge, ...getEstadoBadgeStyle(tramite.estado) }}>{tramite.estado}</span>
                            </td>
                            <td style={styles.td}>{formatoFecha(tramite.fechaRadicacion)}</td>
                            <td style={styles.td}>{formatoFecha(tramite.fechaVencimiento)}</td>
                            <td style={styles.td}>
                              <button
                                style={styles.btnVer}
                                onClick={() => setTramiteExpandidoId((prev) => (prev === tramite.id ? null : tramite.id))}
                              >
                                {expandido ? 'Ocultar' : 'Ver'}
                              </button>
                            </td>
                          </tr>
                          {expandido ? (
                            <tr key={`detalle-${tramite.id}`}>
                              <td colSpan={7} style={styles.td}>
                                <div style={styles.adminDetalle}>
                                  <h3 style={styles.adminDetalleTitle}>Detalle de Solicitud</h3>
                                  <div style={styles.adminDetalleGrid}>
                                    <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Radicado:</span> {tramite.numeroRadicado || '-'}</p>
                                    <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Solicitante:</span> {tramite.nombreSolicitante || '-'}</p>
                                    <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Tipo:</span> {tramite.tipoTramite || '-'}</p>
                                    <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Estado:</span> {tramite.estado || '-'}</p>
                                    <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Documento:</span> {tramite.tipoDocumento || '-'} {tramite.numeroDocumento ? `- ${tramite.numeroDocumento}` : ''}</p>
                                    <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Correo:</span> {tramite.correoElectronico || '-'}</p>
                                    <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Dirección:</span> {tramite.direccionResidencia || '-'}</p>
                                    <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Barrio:</span> {tramite.barrioResidencia || '-'}</p>
                                    <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Fecha Radicación:</span> {formatoFecha(tramite.fechaRadicacion)}</p>
                                    <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Fecha Vencimiento:</span> {formatoFecha(tramite.fechaVencimiento)}</p>
                                    <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Fecha Vigencia:</span> {formatoFecha(tramite.fechaVigencia)}</p>
                                    <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Observaciones:</span> {tramite.observaciones || '-'}</p>
                                  </div>
                                </div>
                              </td>
                            </tr>
                          ) : null}
                        </Fragment>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            ) : null}
          </div>

          <div style={styles.adminCard}>
            <div style={styles.seccionHeader}>
              <h2 style={{ ...styles.adminCardTitle, marginBottom: 0 }}>Certificados Generados ({certificadosGeneradosFiltrados.length})</h2>
              <button
                style={styles.btnToggleSeccion}
                onClick={() => setCertificadosExpandido((prev) => !prev)}
              >
                {certificadosExpandido ? 'Ocultar' : 'Mostrar'}
              </button>
            </div>

            {certificadosExpandido ? (
              <>
                <div style={styles.filtrosCert}>
                  <input
                    style={styles.inputFiltro}
                    placeholder="Filtrar por radicado"
                    value={filtroCertRadicado}
                    onChange={(e) => setFiltroCertRadicado(e.target.value)}
                  />
                  <input
                    style={styles.inputFiltro}
                    placeholder="Filtrar por solicitante"
                    value={filtroCertNombre}
                    onChange={(e) => setFiltroCertNombre(e.target.value)}
                  />
                  <select style={styles.inputFiltro} value={filtroCertTipo} onChange={(e) => setFiltroCertTipo(e.target.value)}>
                    <option value="todos">Todas las respuestas</option>
                    <option value="positiva">Positivas</option>
                    <option value="negativa">Negativas</option>
                  </select>
                </div>

                {certificadosGeneradosFiltrados.length === 0 ? (
                  <p>No hay certificados generados para los filtros seleccionados.</p>
                ) : (
                  <div style={styles.listaCertificados}>
                    {certificadosGeneradosFiltrados.map((cert) => (
                      <div key={`cert-admin-${cert.id}`} style={styles.certItem}>
                        <p style={styles.certMeta}><strong>{cert.numeroRadicado}</strong> · {cert.nombreSolicitante}</p>
                        <p style={styles.certMeta}>Respuesta: {cert.estado === 'FINALIZADO' ? 'Positiva' : 'Negativa'} · Tipo: {cert.tipo_certificado || cert.tipoTramite || 'Certificado'}</p>
                        <p style={styles.certMeta}>Fecha: {formatoFecha(cert.fechaVerificacion || cert.fechaFirmaAlcalde || cert.fechaRadicacion)}</p>
                      </div>
                    ))}
                  </div>
                )}
              </>
            ) : null}
          </div>
        </main>
      </div>
    );
  }

  return null;
}
