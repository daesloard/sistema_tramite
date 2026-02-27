import { useState } from 'react';
import {
  verificarCertificado,
  consultarSolicitudesResueltas,
  descargarCertificadoGenerado,
} from '../services/api';

const styles = {
  contenedor: {
    minHeight: '100vh',
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    padding: 'clamp(0.75rem, 4vw, 2rem) clamp(0.75rem, 4vw, 1rem)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  caja: {
    background: 'white',
    borderRadius: '12px',
    boxShadow: '0 10px 40px rgba(0, 0, 0, 0.2)',
    maxWidth: '600px',
    width: '100%',
    padding: 'clamp(1rem, 4vw, 3rem) clamp(0.9rem, 4vw, 2rem)',
  },
  encabezado: { textAlign: 'center', marginBottom: '2rem' },
  titulo: { color: '#333', fontSize: 'clamp(1.2rem, 5vw, 1.8rem)', margin: '0 0 0.5rem 0' },
  subtitulo: { color: '#666', margin: 0, fontSize: 'clamp(0.9rem, 3.2vw, 1rem)' },
  formulario: { marginBottom: '2rem' },
  entradaRadicado: {
    display: 'flex',
    gap: '0.75rem',
    marginBottom: '2rem',
    alignItems: 'stretch',
    flexWrap: 'wrap',
  },
  inputRadicado: {
    flex: '1 1 260px',
    width: 'auto',
    minWidth: '220px',
    padding: '1rem 1.25rem',
    border: '2px solid #ddd',
    borderRadius: '6px',
    fontSize: '1.1rem',
    fontFamily: "'Courier New', monospace",
    letterSpacing: '1px',
    textTransform: 'uppercase',
    boxSizing: 'border-box',
    minHeight: '52px',
  },
  botonBuscar: {
    width: 'auto',
    flex: '0 0 auto',
    padding: '1rem 2.25rem',
    background: 'linear-gradient(135deg, #667eea, #764ba2)',
    color: 'white',
    border: 'none',
    borderRadius: '6px',
    fontSize: '1.05rem',
    fontWeight: 600,
    cursor: 'pointer',
    whiteSpace: 'nowrap',
    minHeight: '52px',
  },
  botonBuscarDisabled: { opacity: 0.6, cursor: 'not-allowed' },
  instrucciones: {
    background: '#f5f5f5',
    padding: '1.5rem',
    borderRadius: '8px',
    borderLeft: '4px solid #667eea',
  },
  instruccionesTitulo: { marginTop: 0, color: '#333', fontSize: '1.1rem' },
  instruccionesLista: { margin: 0, paddingLeft: '1.5rem', color: '#666' },
  instruccionesItem: { marginBottom: '0.75rem', lineHeight: 1.5 },
  resultadoError: {
    textAlign: 'center',
    padding: '2rem',
    background: '#ffebee',
    borderRadius: '8px',
    border: '1px solid #ef5350',
  },
  iconoError: { fontSize: '3rem', marginBottom: '1rem' },
  tituloError: { color: '#c62828', margin: '1rem 0 0.5rem 0' },
  textoError: { color: '#d32f2f', margin: '0.5rem 0' },
  resultadoExitoso: {},
  iconoExito: { fontSize: '3rem', color: '#4CAF50', textAlign: 'center', marginBottom: '1rem' },
  tituloExito: { color: '#333', textAlign: 'center', margin: '0 0 1.5rem 0', fontSize: '1.5rem' },
  resultadoInfo: { marginBottom: '2rem' },
  infoPrincipal: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
    gap: '1rem',
    marginBottom: '1.5rem',
  },
  panelPrincipal: {
    background: 'linear-gradient(135deg, #667eea15, #764ba215)',
    padding: '1.5rem',
    borderRadius: '8px',
    border: '2px solid #667eea30',
    textAlign: 'center',
  },
  etiquetaPanel: { display: 'block', color: '#666', fontSize: '0.9rem', marginBottom: '0.5rem', fontWeight: 500 },
  valorRadicado: {
    display: 'block',
    color: '#333',
    fontSize: '1.2rem',
    fontWeight: 'bold',
    fontFamily: "'Courier New', monospace",
    letterSpacing: '1px',
    wordBreak: 'break-all',
  },
  badgeEstado: {
    display: 'inline-block',
    padding: '0.5rem 1rem',
    borderRadius: '20px',
    color: 'white',
    fontWeight: 600,
    fontSize: '1rem',
  },
  resultadoDetalles: {
    background: '#f9f9f9',
    padding: '1.5rem',
    borderRadius: '8px',
    border: '1px solid #e0e0e0',
    marginBottom: '1.5rem',
  },
  detalle: {
    display: 'flex',
    justifyContent: 'space-between',
    gap: '0.5rem',
    padding: '1rem 0',
    borderBottom: '1px solid #eee',
    alignItems: 'center',
    flexWrap: 'wrap',
  },
  detalleUltimo: { borderBottom: 'none' },
  etiquetaDetalle: { color: '#666', fontWeight: 600, flex: 1 },
  valorDetalle: { color: '#333', fontWeight: 500, textAlign: 'right', flex: 1 },
  valorVigencia: { padding: '0.25rem 0.75rem', borderRadius: '4px', fontWeight: 'bold' },
  valorVigente: { background: '#e8f5e9', color: '#2e7d32' },
  valorVencido: { background: '#ffebee', color: '#c62828' },
  alertaBase: { padding: '1rem', borderRadius: '6px', marginBottom: '1.5rem', textAlign: 'center', fontWeight: 500 },
  alertaVigente: { background: '#e8f5e9', border: '1px solid #81c784', color: '#2e7d32' },
  alertaVencido: { background: '#ffebee', border: '1px solid #ef5350', color: '#c62828' },
  alertaNoEmitido: { background: '#e3f2fd', border: '1px solid #64b5f6', color: '#1565c0' },
  alertaRechazado: { background: '#fff3e0', border: '1px solid #ffb74d', color: '#e65100' },
  detallesNoEmitido: { marginTop: '0.5rem', fontSize: '0.9rem', fontStyle: 'italic' },
  botonReiniciar: {
    width: 'auto',
    padding: '0.65rem 1rem',
    background: 'linear-gradient(135deg, #667eea, #764ba2)',
    color: 'white',
    border: 'none',
    borderRadius: '6px',
    fontSize: '0.92rem',
    fontWeight: 600,
    cursor: 'pointer',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    whiteSpace: 'nowrap',
  },
  resueltasBloque: { marginTop: '1.5rem', padding: '1rem', border: '1px solid #e0e0e0', borderRadius: '8px', background: '#fafafa' },
  resueltasTitulo: { margin: '0 0 1rem 0', color: '#333' },
  resueltasLista: { display: 'grid', gap: '0.75rem' },
  resueltaItem: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: '0.75rem',
    flexWrap: 'wrap',
    padding: '0.75rem',
    border: '1px solid #e5e7eb',
    borderRadius: '6px',
    background: '#fff',
  },
  resueltaTexto: { margin: '0.15rem 0', color: '#444' },
  botonCertificado: {
    border: 'none',
    background: '#4caf50',
    color: '#fff',
    padding: '0.5rem 0.8rem',
    borderRadius: '5px',
    cursor: 'pointer',
    fontWeight: 600,
  },
  sinCertificado: { color: '#9ca3af', fontSize: '0.9rem' },
  resueltasError: { color: '#c62828', margin: 0 },
  resueltasVacio: { margin: 0, color: '#666' },
};

export default function VerificadorCertificado() {
  const [numeroRadicado, setNumeroRadicado] = useState('');
  const [resultado, setResultado] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [buscado, setBuscado] = useState(false);
  const [solicitudesResueltas, setSolicitudesResueltas] = useState([]);
  const [loadingResueltas, setLoadingResueltas] = useState(false);
  const [errorResueltas, setErrorResueltas] = useState('');

  const buscarResueltas = async (numeroDocumento) => {
    if (!numeroDocumento) {
      setSolicitudesResueltas([]);
      return;
    }

    setLoadingResueltas(true);
    setErrorResueltas('');
    try {
      const data = await consultarSolicitudesResueltas(numeroDocumento);
      setSolicitudesResueltas(Array.isArray(data) ? data : []);
    } catch (err) {
      setSolicitudesResueltas([]);
      setErrorResueltas(err.message || 'No se pudieron consultar solicitudes resueltas');
    } finally {
      setLoadingResueltas(false);
    }
  };

  const handleBuscar = async () => {
    if (!numeroRadicado.trim()) {
      setError('Por favor ingresa un número de radicado o código de verificación');
      return;
    }

    setLoading(true);
    setError('');
    setBuscado(true);

    try {
      const data = await verificarCertificado(numeroRadicado);
      setResultado(data);
      await buscarResueltas(data?.numeroDocumento);
    } catch (err) {
      setError(err.message || 'No se pudo verificar el certificado');
      setResultado(null);
      setSolicitudesResueltas([]);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter') {
      handleBuscar();
    }
  };

  const handleReiniciar = () => {
    setNumeroRadicado('');
    setResultado(null);
    setError('');
    setBuscado(false);
    setSolicitudesResueltas([]);
    setErrorResueltas('');
  };

  const abrirCertificado = async (tramiteId) => {
    try {
      await descargarCertificadoGenerado(tramiteId);
    } catch (err) {
      setErrorResueltas(err.message || 'No se pudo abrir el certificado');
    }
  };

  const getEstadoBadge = (estado) => {
    const estados = {
      'RADICADO': { color: '#2196F3', texto: 'Radicado' },
      'EN_VALIDACION': { color: '#FF9800', texto: 'En Validación' },
      'EN_FIRMA': { color: '#9C27B0', texto: 'En Firma' },
      'FINALIZADO': { color: '#4CAF50', texto: 'Finalizado' },
      'RECHAZADO': { color: '#f44336', texto: 'Rechazado' }
    };
    return estados[estado] || { color: '#999', texto: estado };
  };

  return (
    <div style={styles.contenedor}>
      <div style={styles.caja}>
        <div style={styles.encabezado}>
          <h2 style={styles.titulo}>🔍 Verificar Certificado de Residencia</h2>
          <p style={styles.subtitulo}>Ingresa tu número de radicado o código de verificación para consultar el estado de tu solicitud</p>
        </div>

        {!buscado ? (
          <div style={styles.formulario}>
            <div style={styles.entradaRadicado}>
              <input
                type="text"
                placeholder="Ejemplo: RES-20260225112346 o 25112346-260225112346"
                value={numeroRadicado}
                onChange={(e) => setNumeroRadicado(e.target.value.toUpperCase())}
                onKeyPress={handleKeyPress}
                style={styles.inputRadicado}
              />
              <button 
                onClick={handleBuscar} 
                disabled={loading}
                style={{
                  ...styles.botonBuscar,
                  ...(loading ? styles.botonBuscarDisabled : {}),
                }}
              >
                {loading ? 'Buscando...' : 'Verificar'}
              </button>
            </div>

            <div style={styles.instrucciones}>
              <h3 style={styles.instruccionesTitulo}>¿Cómo usar este servicio?</h3>
              <ul style={styles.instruccionesLista}>
                <li style={styles.instruccionesItem}>Ingresa el número de radicado que recibiste por correo</li>
                <li style={styles.instruccionesItem}>También puedes usar el código único de verificación del certificado</li>
                <li style={styles.instruccionesItem}>Podrás ver el estado actual de tu solicitud</li>
                <li style={styles.instruccionesItem}>Verifica si tu certificado aún está vigente (6 meses)</li>
                <li style={styles.instruccionesItem}>El sistema actualizará cada que tu solicitud cambie de estado</li>
              </ul>
            </div>
          </div>
        ) : null}

        {error && (
          <div style={styles.resultadoError}>
            <div style={styles.iconoError}>⚠️</div>
            <h3 style={styles.tituloError}>No se encontró información</h3>
            <p style={styles.textoError}>{error}</p>
            <button onClick={handleReiniciar} style={styles.botonReiniciar}>
              Buscar Otro Certificado
            </button>
          </div>
        )}

        {resultado && (
          <div style={styles.resultadoExitoso}>
            <div style={styles.iconoExito}>✓</div>
            <h3 style={styles.tituloExito}>Certificado Encontrado</h3>

            <div style={styles.resultadoInfo}>
              <div style={styles.infoPrincipal}>
                <div style={styles.panelPrincipal}>
                  <span style={styles.etiquetaPanel}>Radicado</span>
                  <span style={styles.valorRadicado}>{resultado.numeroRadicado}</span>
                </div>
                
                <div style={styles.panelPrincipal}>
                  <span style={styles.etiquetaPanel}>Estado del Trámite</span>
                  <span 
                    style={{
                      ...styles.badgeEstado,
                      backgroundColor: getEstadoBadge(resultado.estado).color,
                    }}
                  >
                    {getEstadoBadge(resultado.estado).texto}
                  </span>
                </div>
              </div>

              <div style={styles.resultadoDetalles}>
                <div style={styles.detalle}>
                  <span style={styles.etiquetaDetalle}>Nombre del Solicitante</span>
                  <span style={styles.valorDetalle}>{resultado.nombreSolicitante}</span>
                </div>
                
                <div style={styles.detalle}>
                  <span style={styles.etiquetaDetalle}>Número de Documento</span>
                  <span style={styles.valorDetalle}>{resultado.numeroDocumento}</span>
                </div>

                <div style={styles.detalle}>
                  <span style={styles.etiquetaDetalle}>Código de Verificación</span>
                  <span style={styles.valorDetalle}>{resultado.codigoVerificacion || 'No asignado'}</span>
                </div>

                <div style={styles.detalle}>
                  <span style={styles.etiquetaDetalle}>Lugar de Expedición</span>
                  <span style={styles.valorDetalle}>{resultado.lugarExpedicionDocumento || 'No registrado'}</span>
                </div>

                <div style={styles.detalle}>
                  <span style={styles.etiquetaDetalle}>Barrio</span>
                  <span style={styles.valorDetalle}>{resultado.barrioResidencia || 'No registrado'}</span>
                </div>

                <div style={{ ...styles.detalle, ...styles.detalleUltimo }}>
                  <span style={styles.etiquetaDetalle}>Fecha de Radicación</span>
                  <span style={styles.valorDetalle}>
                    {new Date(resultado.fechaRadicacion).toLocaleDateString('es-CO')}
                  </span>
                </div>
              </div>

              {resultado.documentoIntegro === true && (
                <div style={{ ...styles.alertaBase, ...styles.alertaVigente }}>
                  <p>✓ Integridad validada: el documento coincide con el hash registrado en el sistema.</p>
                </div>
              )}

              {resultado.documentoIntegro === false && (
                <div style={{ ...styles.alertaBase, ...styles.alertaVencido }}>
                  <p>⚠️ Integridad no válida: el documento no coincide con el hash registrado.</p>
                </div>
              )}

              {/* MOSTRAR VIGENCIA SOLO SI EL CERTIFICADO FUE EMITIDO (FINALIZADO) */}
              {resultado.certificadoEmitido ? (
                <>
                  <div style={styles.resultadoDetalles}>
                    <div style={styles.detalle}>
                      <span style={styles.etiquetaDetalle}>Fecha de Vigencia del Certificado</span>
                      <span style={styles.valorDetalle}>
                        {new Date(resultado.fechaVigencia).toLocaleDateString('es-CO')}
                      </span>
                    </div>

                    <div style={{ ...styles.detalle, ...styles.detalleUltimo }}>
                      <span style={styles.etiquetaDetalle}>Estado del Certificado</span>
                      <span
                        style={{
                          ...styles.valorDetalle,
                          ...styles.valorVigencia,
                          ...(resultado.vigente ? styles.valorVigente : styles.valorVencido),
                        }}
                      >
                        {resultado.vigente ? '✓ Vigente' : '✗ Vencido'}
                      </span>
                    </div>
                  </div>

                  {resultado.vigente && (
                    <div style={{ ...styles.alertaBase, ...styles.alertaVigente }}>
                      <p>✓ Tu certificado es válido y está en vigencia. Puedes presentarlo en cualquier momento.</p>
                    </div>
                  )}

                  {!resultado.vigente && (
                    <div style={{ ...styles.alertaBase, ...styles.alertaVencido }}>
                      <p>⚠️ Tu certificado ha vencido. Para obtener uno nuevo, deberás realizar una nueva solicitud.</p>
                    </div>
                  )}
                </>
              ) : (
                <div style={{ ...styles.alertaBase, ...styles.alertaNoEmitido }}>
                  <p>📋 El certificado aún no ha sido emitido. Tu solicitud se encuentra en estado: <strong>{getEstadoBadge(resultado.estado).texto}</strong></p>
                  {resultado.mensaje && <p style={styles.detallesNoEmitido}>{resultado.mensaje}</p>}
                </div>
              )}

              {/* MOSTRAR OBSERVACIONES SI FUE RECHAZADO */}
              {resultado.estado === 'RECHAZADO' && resultado.observaciones && (
                <div style={{ ...styles.alertaBase, ...styles.alertaRechazado }}>
                  <p><strong>Razón del rechazo:</strong> {resultado.observaciones}</p>
                </div>
              )}

              <div style={styles.resueltasBloque}>
                <h4 style={styles.resueltasTitulo}>Solicitudes ya resueltas del solicitante</h4>
                {loadingResueltas ? <p>Cargando solicitudes resueltas...</p> : null}
                {errorResueltas ? <p style={styles.resueltasError}>{errorResueltas}</p> : null}

                {!loadingResueltas && !errorResueltas && solicitudesResueltas.length === 0 ? (
                  <p style={styles.resueltasVacio}>No hay solicitudes resueltas para este documento.</p>
                ) : null}

                {!loadingResueltas && solicitudesResueltas.length > 0 ? (
                  <div style={styles.resueltasLista}>
                    {solicitudesResueltas.map((item) => (
                      <div key={item.id} style={styles.resueltaItem}>
                        <div>
                          <p style={styles.resueltaTexto}><strong>Radicado:</strong> {item.numeroRadicado}</p>
                          <p style={styles.resueltaTexto}><strong>Estado:</strong> {getEstadoBadge(item.estado).texto}</p>
                          <p style={styles.resueltaTexto}><strong>Fecha:</strong> {new Date(item.fechaRadicacion).toLocaleDateString('es-CO')}</p>
                        </div>
                        {item.estado === 'FINALIZADO' && item.certificadoDisponible ? (
                          <button style={styles.botonCertificado} onClick={() => abrirCertificado(item.id)}>
                            Ver Certificado
                          </button>
                        ) : (
                          <span style={styles.sinCertificado}>Sin certificado PDF</span>
                        )}
                      </div>
                    ))}
                  </div>
                ) : null}
              </div>
            </div>

            <button onClick={handleReiniciar} style={styles.botonReiniciar}>
              ← Hacer Otra Búsqueda
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
