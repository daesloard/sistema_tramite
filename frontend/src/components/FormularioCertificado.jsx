import { useState } from 'react';
import { radicarCertificadoResidencia } from '../services/api';

import { getFormularioCertificadoStyles } from '../styles/components/FormularioCertificadoStyles';
const styles = getFormularioCertificadoStyles();

export default function FormularioCertificado({ onIrAVerificar }) {
  const [paso, setPaso] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [radicacion, setRadicacion] = useState(null);

  const [formData, setFormData] = useState({
    nombre: '',
    tipoDocumento: 'CC',
    numeroDocumento: '',
    lugarExpedicionDocumento: '',
    direccionResidencia: '',
    barrioResidencia: '',
    telefono: '',
    correoElectronico: '',
    documento_solicitud_base64: '',
    documento_identidad_base64: '',
    documento_solicitud_nombre: '',
    documento_solicitud_tipo: '',
    documento_identidad_nombre: '',
    documento_identidad_tipo: '',
    tipo_certificado: 'SISBEN',
    certificado_base64: '',
    certificado_nombre: '',
    certificado_tipo: '',
  });

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
    setError('');
  };

  const handleFileChange = (e, fieldName) => {
    const file = e.target.files[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (event) => {
        const base64 = event.target.result.split(',')[1];
        const baseKey = `${fieldName}_base64`;
        const nameKey = `${fieldName}_nombre`;
        const typeKey = `${fieldName}_tipo`;
        setFormData((prev) => ({
          ...prev,
          [baseKey]: base64,
          [nameKey]: file.name,
          [typeKey]: file.type || 'application/octet-stream',
        }));
      };
      reader.readAsDataURL(file);
    }
  };

  const validarPaso = () => {
    switch (paso) {
      case 2:
        if (!formData.nombre || !formData.numeroDocumento || !formData.lugarExpedicionDocumento) {
          setError('Por favor completa todos los campos requeridos');
          return false;
        }
        break;
      case 3:
        if (!formData.barrioResidencia || !formData.telefono || !formData.correoElectronico) {
          setError('Por favor completa todos los campos requeridos');
          return false;
        }
        if (!formData.documento_solicitud_base64 || !formData.documento_identidad_base64) {
          setError('Por favor carga los documentos de solicitud e identidad');
          return false;
        }
        break;
      case 4:
        if (!formData.certificado_base64) {
          setError('Por favor carga el certificado');
          return false;
        }
        break;
      default:
        return true;
    }
    return true;
  };

  const handleNext = () => {
    if (validarPaso()) {
      setPaso(paso + 1);
    }
  };

  const handleBack = () => {
    setPaso(paso - 1);
    setError('');
  };

  const handleSubmit = async () => {
    if (!validarPaso()) return;

    setLoading(true);
    setError('');

    try {
      const solicitud = {
        nombre: formData.nombre,
        tipoDocumento: formData.tipoDocumento,
        numeroDocumento: formData.numeroDocumento,
        lugarExpedicionDocumento: formData.lugarExpedicionDocumento,
        direccionResidencia: formData.direccionResidencia,
        barrioResidencia: formData.barrioResidencia,
        telefono: formData.telefono,
        correoElectronico: formData.correoElectronico,
        documento_solicitud_base64: formData.documento_solicitud_base64,
        documento_solicitud_nombre: formData.documento_solicitud_nombre,
        documento_solicitud_tipo: formData.documento_solicitud_tipo,
        documento_identidad_base64: formData.documento_identidad_base64,
        documento_identidad_nombre: formData.documento_identidad_nombre,
        documento_identidad_tipo: formData.documento_identidad_tipo,
        tipo_certificado: formData.tipo_certificado,
        certificado_base64: formData.certificado_base64,
        certificado_nombre: formData.certificado_nombre,
        certificado_tipo: formData.certificado_tipo,
      };

      const respuesta = await radicarCertificadoResidencia(solicitud);
      if (!respuesta?.tramiteId) {
        throw new Error('No fue posible confirmar el ID del trámite radicado');
      }
      setRadicacion(respuesta);
      setPaso(5);
    } catch (err) {
      setError(err.message || 'Error al radicar la solicitud');
    } finally {
      setLoading(false);
    }
  };

  const handleNuevaSolicitud = () => {
    setPaso(1);
    setFormData({
      nombre: '',
      tipoDocumento: 'CC',
      numeroDocumento: '',
      lugarExpedicionDocumento: '',
      direccionResidencia: '',
      barrioResidencia: '',
      telefono: '',
      correoElectronico: '',
      documento_solicitud_base64: '',
      documento_identidad_base64: '',
      documento_solicitud_nombre: '',
      documento_solicitud_tipo: '',
      documento_identidad_nombre: '',
      documento_identidad_tipo: '',
      tipo_certificado: 'SISBEN',
      certificado_base64: '',
      certificado_nombre: '',
      certificado_tipo: '',
    });
    setRadicacion(null);
    setError('');
  };

  // PASO 1: Seleccionar tipo de trámite
  if (paso === 1) {
    return (
      <div style={styles.formularioContenedor}>
        <div style={styles.pasoContenedor}>
          <h2 style={styles.h2}>Seleccionar Tipo de Trámite</h2>
          <div style={styles.opciones}>
            <button 
              style={styles.botonTramite}
              onClick={() => setPaso(2)}
            >
              <span style={styles.icono}>📋</span>
              <span style={styles.titulo}>Certificado de Residencia</span>
              <span style={styles.descripcion}>Solicitar certificado de residencia del municipio</span>
            </button>
          </div>
        </div>
      </div>
    );
  }

  // PASO 2: Datos básicos
  if (paso === 2) {
    return (
      <div style={styles.formularioContenedor}>
        <div style={styles.pasoContenedor}>
          <h2 style={styles.h2}>Paso 1: Datos Básicos</h2>
          <div style={styles.barraProgreso}>
            <div style={{ ...styles.progreso, width: '25%' }}></div>
          </div>

          {error && <div style={styles.alertaError}>{error}</div>}

          <div style={styles.formularioGrupo}>
            <label style={styles.label}>Nombre Completo *</label>
            <input
              type="text"
              name="nombre"
              value={formData.nombre}
              onChange={handleInputChange}
              placeholder="Tu nombre completo"
              style={styles.input}
            />
          </div>

          <div style={styles.formularioGrupo}>
            <label style={styles.label}>Tipo de Documento *</label>
            <select
              name="tipoDocumento"
              value={formData.tipoDocumento}
              onChange={handleInputChange}
              style={styles.input}
            >
              <option value="CC">Cédula de Ciudadanía</option>
              <option value="CE">Cédula de Extranjería</option>
              <option value="TI">Tarjeta de Identidad</option>
              <option value="PA">Pasaporte</option>
            </select>
          </div>

          <div style={styles.formularioGrupo}>
            <label style={styles.label}>Número de Documento *</label>
            <input
              type="text"
              name="numeroDocumento"
              value={formData.numeroDocumento}
              onChange={handleInputChange}
              placeholder="Tu número de documento"
              style={styles.input}
            />
          </div>

          <div style={styles.formularioGrupo}>
            <label style={styles.label}>Lugar de Expedición del Documento * </label>
            <label style={{ ...styles.label, fontSize: '0.9rem', color: '#555' }}>
              (Ciudad o Municipio seguido de Departamento, ej: "Cabuyaro, Meta")
            </label>              
            <input
              type="text"
              name="lugarExpedicionDocumento"
              value={formData.lugarExpedicionDocumento}
              onChange={handleInputChange}
              placeholder="Ciudad o Municipio (Departamento)"
              style={styles.input}
            />
          </div>

          <div style={styles.botonesAccion}>
            <button style={styles.botonSecundario} onClick={handleBack}>
              ← Atrás
            </button>
            <button style={styles.botonPrimario} onClick={handleNext}>
              Siguiente →
            </button>
          </div>
        </div>
      </div>
    );
  }

  // PASO 3: Datos de contacto y documentos
  if (paso === 3) {
    return (
      <div style={styles.formularioContenedor}>
        <div style={styles.pasoContenedor}>
          <h2 style={styles.h2}>Paso 2: Información de Contacto y Documentos</h2>
          <div style={styles.barraProgreso}>
            <div style={{ ...styles.progreso, width: '50%' }}></div>
          </div>

          {error && <div style={styles.alertaError}>{error}</div>}

          <div style={styles.formularioGrupo}>
            <label style={styles.label}>Dirección de Residencia (Opcional)</label>
            <label style={{ ...styles.label, fontSize: '0.9rem', color: '#555' }}>Si no aplica, puedes dejarlo en blanco o escribir No Aplica.</label>
            <input
              type="text"
              name="direccionResidencia"
              value={formData.direccionResidencia}
              onChange={handleInputChange}
              placeholder="Tu dirección completa (opcional)"
              style={styles.input}
            />
          </div>

          <div style={styles.formularioGrupo}>
            <label style={styles.label}>Barrio o Vereda *</label>
            <input
              type="text"
              name="barrioResidencia"
              value={formData.barrioResidencia}
              onChange={handleInputChange}
              placeholder="Barrio o Vereda de residencia"
              style={styles.input}
            />
          </div>

          <div style={styles.formularioGrupo}>
            <label style={styles.label}>Teléfono *</label>
            <input
              type="tel"
              name="telefono"
              value={formData.telefono}
              onChange={handleInputChange}
              placeholder="Tu número de teléfono"
              style={styles.input}
            />
          </div>

          <div style={styles.formularioGrupo}>
            <label style={styles.label}>Correo Electrónico *</label>
            <input
              type="email"
              name="correoElectronico"
              value={formData.correoElectronico}
              onChange={handleInputChange}
              placeholder="tu@email.com"
              style={styles.input}
            />
          </div>

          <div style={styles.seccionArchivos}>
            <h3 style={{ ...styles.h3, marginTop: 0, color: '#333' }}>Documentos Requeridos</h3>
            <p style={styles.avisoPeso}>Peso máximo por archivo: 10 MB</p>
            
            <div style={styles.formularioGrupo}>
              <label style={styles.label}>Documento de Solicitud *</label>
              <input
                type="file"
                accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
                onChange={(e) => handleFileChange(e, 'documento_solicitud')}
                style={styles.inputFile}
              />
              {formData.documento_solicitud_base64 && (
                <span style={styles.archivoCargado}>✓ Archivo cargado</span>
              )}
            </div>

            <div style={{ ...styles.formularioGrupo, marginBottom: 0 }}>
              <label style={styles.label}>Copia de Documento de Identidad *</label>
              <input
                type="file"
                accept=".pdf,.jpg,.jpeg,.png"
                onChange={(e) => handleFileChange(e, 'documento_identidad')}
                style={styles.inputFile}
              />
              {formData.documento_identidad_base64 && (
                <span style={styles.archivoCargado}>✓ Archivo cargado</span>
              )}
            </div>
          </div>

          <div style={styles.botonesAccion}>
            <button style={styles.botonSecundario} onClick={handleBack}>
              ← Atrás
            </button>
            <button style={styles.botonPrimario} onClick={handleNext}>
              Siguiente →
            </button>
          </div>
        </div>
      </div>
    );
  }

  // PASO 4: Certificado de validación
  if (paso === 4) {
    return (
      <div style={styles.formularioContenedor}>
        <div style={styles.pasoContenedor}>
          <h2 style={styles.h2}>Paso 3: Certificado de Residencia</h2>
          <div style={styles.barraProgreso}>
            <div style={{ ...styles.progreso, width: '75%' }}></div>
          </div>

          {error && <div style={styles.alertaError}>{error}</div>}

          <div style={styles.formularioGrupo}>
            <label style={styles.label}>Tipo de Certificado *</label>
            <select
              name="tipo_certificado"
              value={formData.tipo_certificado}
              onChange={handleInputChange}
              style={styles.input}
            >
              <option value="SISBEN">Certificado SISBEN</option>
              <option value="ELECTORAL">Certificado Electoral</option>
              <option value="JAC">Certificado de Junta de Acción Comunal</option>
            </select>
          </div>

          <div style={styles.formularioGrupo}>
            <label style={styles.label}>Cargar Certificado *</label>
            <p style={styles.avisoPeso}>Peso máximo por archivo: 10 MB</p>
            <input
              type="file"
              accept=".pdf,.jpg,.jpeg,.png"
              onChange={(e) => handleFileChange(e, 'certificado')}
              style={styles.inputFile}
            />
            {formData.certificado_base64 && (
              <span style={styles.archivoCargado}>✓ Archivo cargado</span>
            )}
          </div>

          <div style={styles.resumen}>
            <h3 style={{ ...styles.h3, marginTop: 0 }}>Resumen de tu Solicitud</h3>
            <div style={styles.resumenItem}>
              <strong>Nombre:</strong> {formData.nombre}
            </div>
            <div style={styles.resumenItem}>
              <strong>Documento:</strong> {formData.tipoDocumento} - {formData.numeroDocumento}
            </div>
            <div style={styles.resumenItem}>
              <strong>Lugar de Expedición:</strong> {formData.lugarExpedicionDocumento}
            </div>
            <div style={styles.resumenItem}>
              <strong>Dirección:</strong> {formData.direccionResidencia}
            </div>
            <div style={styles.resumenItem}>
              <strong>Barrio o Vereda:</strong> {formData.barrioResidencia}
            </div>
            <div style={styles.resumenItem}>
              <strong>Email:</strong> {formData.correoElectronico}
            </div>
            <div style={{ ...styles.resumenItem, ...styles.resumenItemUltimo }}>
              <strong>Tipo de Certificado:</strong> {formData.tipo_certificado}
            </div>
          </div>

          <div style={styles.botonesAccion}>
            <button style={styles.botonSecundario} onClick={handleBack}>
              ← Atrás
            </button>
            <button 
              style={{ ...styles.botonPrimario, ...(loading ? styles.botonPrimarioDisabled : {}) }}
              onClick={handleSubmit}
              disabled={loading}
            >
              {loading ? 'Enviando...' : 'Radicar Solicitud ✓'}
            </button>
          </div>
        </div>
      </div>
    );
  }

  // PASO 5: Confirmación y radicado
  if (paso === 5 && radicacion) {
    return (
      <div style={styles.formularioContenedor}>
        <div style={styles.pasoExito}>
          <div style={styles.iconoExito}>✓</div>
          <h2 style={styles.h2}>¡Solicitud Radicada Exitosamente!</h2>
          
          <div style={styles.infoRadicacion}>
            <div style={styles.radicadoPrincipal}>
              <p style={styles.etiqueta}>Tu Número de Radicado</p>
              <p style={styles.numeroRadicado}>{radicacion.numeroRadicado}</p>
            </div>

            <div style={styles.detalles}>
              <div style={styles.detalleItem}>
                <strong>Fecha de Solicitud:</strong>
                <span>
                  {new Date(radicacion.fechaSolicitud).toLocaleDateString('es-CO')}
                </span>
              </div>
              <div style={styles.detalleItem}>
                <strong>Fecha Estimada de Entrega:</strong>
                <span>
                  {new Date(radicacion.fechaVencimiento).toLocaleDateString('es-CO')}
                </span>
              </div>
              <div style={{ ...styles.detalleItem, ...styles.detalleItemUltimo }}>
                <strong>Estado:</strong>
                <span style={styles.estadoBadge}>{radicacion.estado}</span>
              </div>
            </div>

            <div style={styles.mensajeConfirmacion}>
              <p style={{ margin: '0.5rem 0' }}>
                Hemos enviado la confirmación y los detalles de tu solicitud a tu correo electrónico.
              </p>
              <p style={styles.nota}>
                Estará lista en un término máximo de 10 días hábiles (de lunes a viernes).
              </p>
            </div>

            <div style={styles.proximosPasos}>
              <h3 style={{ ...styles.h3, marginTop: 0 }}>Próximos Pasos</h3>
              <ol style={styles.ol}>
                <li style={styles.li}>Tu solicitud será verificada por el área de trámites</li>
                <li style={styles.li}>Se enviará al despacho del Alcalde para firma</li>
                <li style={styles.li}>Recibirás tu certificado por correo electrónico</li>
                <li style={{ ...styles.li, marginBottom: 0 }}>También estará disponible en ventanilla para impresión</li>
              </ol>
            </div>
          </div>

          <div style={styles.botonesFinales}>
            <button style={styles.botonSecundario} onClick={handleNuevaSolicitud}>
              Realizar Nueva Solicitud
            </button>
            <button style={styles.botonPrimario} onClick={() => {
              if (typeof onIrAVerificar === 'function') {
                onIrAVerificar();
                return;
              }
              localStorage.setItem('sistema_tramites_vista', 'verificar');
              window.location.assign('/');
            }}>
              Consultar Estado
            </button>
          </div>
        </div>
      </div>
    );
  }

  return null;
}