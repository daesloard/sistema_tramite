import { Fragment, useState } from 'react';
import { regenerarPdf } from '../../services/tramiteService';

export default function AdminTramiteItem({
    tramite,
    expandido,
    toggleDetalle,
    filasRef,
    documentStatus,
    auditoriaEventos,
    loadingDocumentStatus,
    loadingAuditoria,
    archivoCarga,
    subiendoDocumentoKey,
    mensajeGestion,
    setMensajeGestion,
    seleccionarArchivo,
    subirDocumento,
    abrirDocumento,
    descargarDocumento,
    abrirDocumentoGenerado,
    descargarDocumentoGenerado,
    notificarVerificador,
    notificandoVerificador,
    obtenerDocumentos,
    obtenerFaltantesDocumentales,
    getEstadoBadgeStyle,
    formatoFecha,
    formatoFechaHora,
    styles
}) {
    const [regenerandoPdf, setRegenerandoPdf] = useState(false);
    const [mensajePdf, setMensajePdf] = useState('');
    const documentosAdmin = obtenerDocumentos(tramite);
    const faltantesDocumentales = obtenerFaltantesDocumentales(tramite.id, documentosAdmin);
    const certificadoGeneradoDisponible = !!documentStatus?.certificadoGeneradoDisponible;
    const almacenamientoCertificadoGenerado = documentStatus?.certificadoGeneradoAlmacenamiento
        || (tramite?.ruta_certificado_final?.startsWith('drive:') ? 'DRIVE' : 'BD');
    const documentosCompletos = documentosAdmin.length > 0
        && documentosAdmin.every((doc) => !!documentStatus?.[doc.key]?.cargado);

    return (
        <Fragment>
            <tr ref={(elemento) => {
                if (elemento) {
                    filasRef.current[tramite.id] = elemento;
                } else {
                    delete filasRef.current[tramite.id];
                }
            }}>
                <td style={{ ...styles.td, ...styles.celdaRadicado }}>{tramite.numeroRadicado}</td>
                <td style={styles.td}>{tramite.nombreSolicitante}</td>
                <td style={styles.td}>{tramite.tipoTramite}</td>
                <td style={styles.td}>
                    <span style={{ ...styles.badge, ...getEstadoBadgeStyle(tramite.estado) }}>{tramite.estado}</span>
                                {documentStatus && (
                                    faltantesDocumentales.length > 0
                                        ? <div style={styles.badgeDocsPendientes}>Faltan {faltantesDocumentales.length} documento(s)</div>
                                        : (tramite.estado !== 'RECHAZADO' ? <div style={styles.badgeDocsOk}>Documentos en regla</div> : <div style={styles.badgeDocsOk}>Documentos verificados</div>)
                                )}
                </td>
                <td style={styles.td}>{formatoFecha(tramite.fechaRadicacion)}</td>
                <td style={styles.td}>{formatoFecha(tramite.fechaVencimiento)}</td>
                <td style={styles.td}>
                    <button
                        style={styles.btnVer}
                        onClick={toggleDetalle}
                    >
                        {expandido ? 'Ocultar' : 'Ver'}
                    </button>
                </td>
            </tr>
            {expandido && (
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
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Certificado generado:</span> {certificadoGeneradoDisponible ? 'Disponible' : 'Pendiente'}</p>
                                {!certificadoGeneradoDisponible && (
                                    <button
                                        style={{ ...styles.btnDocDesc, marginTop: 8 }}
                                        onClick={() => regenerarPdf(tramite.id)}
                                    >
                                        🔄 Regenerar PDF
                                    </button>
                                )}
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Almacenamiento certificado final:</span> {almacenamientoCertificadoGenerado}</p>
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Drive habilitado:</span> {documentStatus?.driveHabilitado ? 'Sí' : 'No'}</p>
                                <p style={styles.adminDetalleItem}><span style={styles.adminDetalleLabel}>Carpeta Drive trámite:</span> {documentStatus?.driveFolderId || '-'}</p>
                            </div>

                            <div style={styles.adminDocs}>
                                <h4 style={{ margin: 0, color: '#1f2937' }}>Documentos del trámite</h4>
                                {loadingDocumentStatus && <p style={styles.adminNota}>Cargando estado documental...</p>}
                                {!loadingDocumentStatus && documentStatus && (
                                    faltantesDocumentales.length > 0
                                        ? <p style={styles.faltantesTexto}>Faltantes detectados: {faltantesDocumentales.join(', ')}</p>
                                        : <p style={{ ...styles.faltantesTexto, color: '#166534' }}>Todos los documentos requeridos están cargados.</p>
                                )}
                                {documentosAdmin.map((doc) => {
                                    const disponible = !!documentStatus?.[doc.key]?.cargado;
                                    const claveArchivo = `${tramite.id}-${doc.key}`;
                                    const archivoSeleccionado = archivoCarga[claveArchivo];
                                    const subiendoEste = subiendoDocumentoKey === claveArchivo;
                                    return (
                                        <div key={`${tramite.id}-${doc.key}`} style={styles.adminDocItem}>
                                            <span style={styles.adminDocLabel}>{doc.label}</span>
                                            <div style={styles.adminDocBtns}>
                                                {disponible ? (
                                                    <>
                                                        <button style={styles.btnDocVer} onClick={() => abrirDocumento(tramite.id, doc.key)}>Ver</button>
                                                        <button style={styles.btnDocDesc} onClick={() => descargarDocumento(tramite.id, doc.key)}>Descargar</button>
                                                    </>
                                                ) : (
                                                    <span style={styles.adminNota}>No disponible</span>
                                                )}
                                                <input
                                                    type="file"
                                                    accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
                                                    onChange={(e) => seleccionarArchivo(tramite.id, doc.key, e.target.files?.[0])}
                                                    style={{ maxWidth: '220px' }}
                                                />
                                                <button
                                                    style={{ ...styles.btnDocDesc, ...(subiendoEste ? styles.btnGuardarUsuarioDisabled : {}) }}
                                                    onClick={() => subirDocumento(tramite, doc.key)}
                                                    disabled={subiendoEste || !archivoSeleccionado}
                                                >
                                                    {subiendoEste ? 'Subiendo...' : (disponible ? 'Reemplazar' : 'Cargar')}
                                                </button>
                                            </div>
                                        </div>
                                    );
                                })}

                                {(tramite.estado === 'FINALIZADO' || tramite.estado === 'RECHAZADO') && (
                                    <div style={styles.adminDocItem}>
                                        <span style={styles.adminDocLabel}>Certificado generado (firmado)</span>
                                        <div style={styles.adminDocBtns}>
                                            <button style={styles.btnDocVer} onClick={() => abrirDocumentoGenerado(tramite.id)}>Ver</button>
                                            <button style={styles.btnDocDesc} onClick={() => descargarDocumentoGenerado(tramite.id, tramite.numeroRadicado)}>Descargar</button>
                                            {!certificadoGeneradoDisponible && (
                                                <button
                                                    style={{ ...styles.btnDocDesc, marginLeft: 8 }}
                                                    disabled={regenerandoPdf}
                                                    onClick={async () => {
                                                        setRegenerandoPdf(true);
                                                        setMensajePdf('');
                                                        try {
                                                            const resp = await regenerarPdf(tramite.id);
                                                            setMensajePdf(resp.message || 'PDF regenerado correctamente');
                                                        } catch (err) {
                                                            setMensajePdf(err.message || 'Error al regenerar PDF');
                                                        } finally {
                                                            setRegenerandoPdf(false);
                                                        }
                                                    }}
                                                >
                                                    {regenerandoPdf ? 'Regenerando...' : '🔄 Regenerar PDF'}
                                                </button>
                                            )}
                                        </div>
                                        {mensajePdf && (
                                            <div style={{ color: mensajePdf.includes('Error') ? '#d32f2f' : '#166534', marginTop: 8 }}>
                                                {mensajePdf}
                                            </div>
                                        )}
                                    </div>
                                )}

                                <input
                                    style={styles.inputAdminMensaje}
                                    placeholder="Mensaje para el verificador (opcional)"
                                    value={mensajeGestion}
                                    onChange={(e) => setMensajeGestion(e.target.value)}
                                />
                                <button
                                    style={{ ...styles.btnNotificarVerificador, ...(notificandoVerificador ? styles.btnGuardarUsuarioDisabled : {}) }}
                                    onClick={() => notificarVerificador(tramite)}
                                    disabled={notificandoVerificador || !documentosCompletos}
                                >
                                    {notificandoVerificador
                                        ? 'Notificando...'
                                        : (documentosCompletos
                                            ? '📩 Notificar verificador: documentos en regla'
                                            : '📩 Completa documentos para notificar')}
                                </button>
                                <p style={styles.adminNota}>
                                    {documentosCompletos
                                        ? 'Los documentos están en regla. Ya puedes notificar al verificador para continuar el trámite.'
                                        : 'Carga los documentos faltantes y luego notifica al verificador.'}
                                </p>

                                <div style={styles.adminAuditoria}>
                                    <h4 style={{ margin: 0, color: '#1f2937' }}>Trazabilidad del trámite (auditoría)</h4>
                                    {loadingAuditoria && <p style={styles.adminNota}>Cargando auditoría...</p>}
                                    {!loadingAuditoria && auditoriaEventos.length === 0 && <p style={styles.adminNota}>Aún no hay eventos de auditoría para este trámite.</p>}
                                    {!loadingAuditoria && auditoriaEventos.length > 0 && (
                                        <div style={styles.adminAuditoriaLista}>
                                            {auditoriaEventos.map((evento) => (
                                                <div key={`audit-${tramite.id}-${evento.id}`} style={styles.adminAuditoriaItem}>
                                                    <p style={styles.adminAuditoriaAccion}>{evento.accion || 'EVENTO'}</p>
                                                    <p style={styles.adminAuditoriaMeta}>Fecha: {formatoFechaHora(evento.fechaIntegracion)}</p>
                                                    <p style={styles.adminAuditoriaMeta}>Usuario: {evento.username || 'sistema'}{evento.rol ? ` (${evento.rol})` : ''}</p>
                                                    <p style={styles.adminAuditoriaMeta}>Estado: {evento.estadoAnterior || '-'} → {evento.estadoNuevo || '-'}</p>
                                                    <p style={styles.adminAuditoriaMeta}>Detalle: {evento.descripcion || '-'}</p>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </div>
                            </div>
                        </div>
                    </td>
                </tr>
            )}
        </Fragment>
    );
}
