import { useEffect, useState } from 'react';
import {
  verificarCertificado,
  consultarSolicitudesResueltas,
  descargarCertificadoGenerado,
} from '../services/api';

import { getVerificadorCertificadoStyles } from '../styles/components/VerificadorCertificadoStyles';
const styles = getVerificadorCertificadoStyles();

const FACTORES_VALIDACION = [
  {
    tipo: 'PRIMER_NOMBRE',
    placeholder: 'Primer nombre del solicitante',
    ayuda: 'Primer nombre tal como fue registrado en la solicitud.',
  },
  {
    tipo: 'ULTIMOS_3_DOCUMENTO',
    placeholder: 'Últimos 3 dígitos del documento',
    ayuda: 'Solo los últimos 3 dígitos del número de documento.',
  },
];

export default function VerificadorCertificado() {
  const [numeroRadicado, setNumeroRadicado] = useState('');
  const [factorTipo, setFactorTipo] = useState('');
  const [factorValor, setFactorValor] = useState('');
  const [resultado, setResultado] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [buscado, setBuscado] = useState(false);
  const [solicitudesResueltas, setSolicitudesResueltas] = useState([]);
  const [loadingResueltas, setLoadingResueltas] = useState(false);
  const [errorResueltas, setErrorResueltas] = useState('');

  const factorActual = FACTORES_VALIDACION.find((f) => f.tipo === factorTipo) || FACTORES_VALIDACION[0];

  const elegirFactorAleatorio = () => {
    const idx = Math.floor(Math.random() * FACTORES_VALIDACION.length);
    setFactorTipo(FACTORES_VALIDACION[idx].tipo);
    setFactorValor('');
  };

  useEffect(() => {
    elegirFactorAleatorio();
  }, []);

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

    if (!factorValor.trim()) {
      setError('Por favor ingresa el dato de validación solicitado');
      return;
    }

    setLoading(true);
    setError('');
    setBuscado(true);

    try {
      const data = await verificarCertificado(numeroRadicado, factorTipo, factorValor);
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
    elegirFactorAleatorio();
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
              <input
                type="text"
                placeholder={factorActual.placeholder}
                value={factorValor}
                onChange={(e) => setFactorValor(e.target.value)}
                onKeyPress={handleKeyPress}
                style={{ ...styles.inputRadicado, fontFamily: 'inherit', letterSpacing: 'normal', textTransform: 'none' }}
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

            <p style={{ marginTop: '-1rem', marginBottom: '1.25rem', color: '#4b5563', fontSize: '0.9rem' }}>
              Reto actual: <strong>{factorActual.ayuda}</strong>
            </p>

            <div style={styles.instrucciones}>
              <h3 style={styles.instruccionesTitulo}>¿Cómo usar este servicio?</h3>
              <ul style={styles.instruccionesLista}>
                <li style={styles.instruccionesItem}>Ingresa el número de radicado que recibiste por correo</li>
                <li style={styles.instruccionesItem}>Completa el dato aleatorio de validación que te solicita el sistema</li>
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