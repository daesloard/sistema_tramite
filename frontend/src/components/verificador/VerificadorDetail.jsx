export default function VerificadorDetail({
    selectedSolicitud,
    setSelectedSolicitud,
    consecutivo,
    setConsecutivo,
    observaciones,
    setObservaciones,
    documentosAdjuntos,
    documentStatus,
    loadingDocumentos,
    totalDocumentosMostrados,
    hayDesfaseDocumentos,
    documentosFaltantes,
    documentosObligatoriosCompletos,
    esPendienteSeleccionada,
    textoDiasHabilesSeleccionada,
    procesando,
    enviandoNotificacionAdmin,
    handleAprobar,
    handleRechazar,
    handleNotificarAdmin,
    abrirDocumento,
    descargarDocumento,
    formatearFechaHora,
    isMobile,
    styles
}) {
    return (
        <div style={styles.detalle}>
            <div style={styles.detalleHeader}>
                <h3 style={styles.detalleHeaderH3}>🔍 Verificación Detallada</h3>
                <button
                    style={styles.cerrar}
                    onClick={() => {
                        setSelectedSolicitud(null);
                        setObservaciones('');
                        setConsecutivo('');
                    }}
                >
                    ✕
                </button>
            </div>

            <div style={styles.detalleBody}>
                <div style={styles.seccion}>
                    <h4 style={styles.h4}>📝 Información del Solicitante</h4>
                    <div style={styles.grid2}>
                        <div style={styles.cardInfo}><span style={styles.label}>Nombre</span><p style={styles.value}>{selectedSolicitud.nombreSolicitante}</p></div>
                        <div style={styles.cardInfo}><span style={styles.label}>Documento</span><p style={styles.value}>{selectedSolicitud.numeroDocumento}</p></div>
                        <div style={styles.cardInfo}><span style={styles.label}>Correo</span><p style={styles.value}>{selectedSolicitud.correoElectronico}</p></div>
                        <div style={styles.cardInfo}><span style={styles.label}>Teléfono</span><p style={styles.value}>{selectedSolicitud.telefono}</p></div>
                        <div style={styles.cardInfo}><span style={styles.label}>Dirección</span><p style={styles.value}>{selectedSolicitud.direccionResidencia}</p></div>
                        <div style={styles.cardInfo}><span style={styles.label}>Barrio</span><p style={styles.value}>{selectedSolicitud.barrioResidencia || 'No registrado'}</p></div>
                    </div>
                </div>

                <div style={styles.seccion}>
                    <h4 style={styles.h4}>📋 Información del Trámite</h4>
                    <div style={styles.grid2}>
                        <div style={styles.cardInfo}><span style={styles.label}>Radicado</span><p style={styles.value}>{selectedSolicitud.numeroRadicado}</p></div>
                        <div style={styles.cardInfo}><span style={styles.label}>Estado</span><p style={styles.value}>{selectedSolicitud.estado}</p></div>
                        <div style={styles.cardInfo}><span style={styles.label}>Tipo Certificado</span><p style={styles.value}>{selectedSolicitud.tipo_certificado || 'No especificado'}</p></div>
                        <div style={styles.cardInfo}><span style={styles.label}>Fecha Radicación</span><p style={styles.value}>{formatearFechaHora(selectedSolicitud.fechaRadicacion)}</p></div>
                        {textoDiasHabilesSeleccionada && (
                            <div style={styles.cardInfo}><span style={styles.label}>Tiempo restante</span><p style={styles.value}>{textoDiasHabilesSeleccionada}</p></div>
                        )}
                        <div style={styles.cardInfo}>
                            <span style={styles.label}>Consecutivo Documental</span>
                            <input
                                style={styles.input}
                                value={consecutivo}
                                onChange={(e) => setConsecutivo(e.target.value.replace(/\D/g, '').slice(0, 6))}
                                readOnly={!esPendienteSeleccionada}
                                inputMode="numeric"
                                pattern="[0-9]*"
                                autoComplete="off"
                            />
                        </div>
                    </div>
                </div>

                <div style={styles.seccion}>
                    <h4 style={styles.h4}>📂 Documentos Adjuntos</h4>
                    {hayDesfaseDocumentos && (
                        <div style={styles.warning}>
                            ⚠️ Se detectó una inconsistencia: hay {documentStatus?.totalDocumentosCargados || 0} documento(s) cargado(s) pero solo se muestran {totalDocumentosMostrados}. Intenta recargar la solicitud y, si persiste, reporta este radicado.
                        </div>
                    )}
                    {!loadingDocumentos && !documentosObligatoriosCompletos && esPendienteSeleccionada && (
                        <div style={styles.warning}>
                            ⚠️ Faltan documentos obligatorios: {documentosFaltantes.join(', ')}. No podrás aprobar ni rechazar hasta que estén completos.
                        </div>
                    )}
                    {documentStatus?.driveHabilitado && (
                        <div style={{ ...styles.nota, marginTop: '0.5rem' }}>
                            Carpeta Drive del trámite: {documentStatus?.driveFolderId || 'No asignada aún'}
                        </div>
                    )}
                    <div style={styles.docs}>
                        {documentosAdjuntos.map((doc) => {
                            const disponible = loadingDocumentos ? false : !!documentStatus?.[doc.key]?.cargado;
                            return (
                                <div key={doc.label} style={styles.docItem}>
                                    <span>{doc.label}</span>
                                    {loadingDocumentos ? (
                                        <span style={{ color: '#95a5a6', fontSize: '12px' }}>Cargando...</span>
                                    ) : disponible ? (
                                        <div style={styles.docBtns}>
                                            <button style={styles.btnVer} onClick={() => abrirDocumento(selectedSolicitud.id, doc.key)}>Ver</button>
                                            <button style={styles.btnDesc} onClick={() => descargarDocumento(selectedSolicitud.id, doc.key)}>Descargar</button>
                                        </div>
                                    ) : (
                                        <span style={{ color: '#95a5a6', fontSize: '12px' }}>No disponible</span>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                    {esPendienteSeleccionada && (
                        <div style={{ marginTop: '10px' }}>
                            <button
                                style={{ ...styles.btnNotificar, ...(enviandoNotificacionAdmin || loadingDocumentos ? styles.disabled : {}) }}
                                onClick={handleNotificarAdmin}
                                disabled={enviandoNotificacionAdmin || loadingDocumentos}
                            >
                                {enviandoNotificacionAdmin ? '⏳ Notificando administrador...' : '📩 Notificar al administrador para revisión documental'}
                            </button>
                        </div>
                    )}
                </div>

                <div style={styles.seccion}>
                    <h4 style={styles.h4}>💭 Observaciones</h4>
                    {esPendienteSeleccionada ? (
                        <>
                            <textarea
                                style={styles.textarea}
                                value={observaciones}
                                onChange={(e) => setObservaciones(e.target.value)}
                                placeholder={selectedSolicitud.estado === 'RADICADO' ? 'Escribe observaciones si vas a rechazar, o déjalo en blanco para aprobar...' : 'Escribe aquí tus observaciones'}
                            />
                            <div style={styles.acciones}>
                                <button
                                    style={{
                                        ...styles.btnAprobar,
                                        ...(isMobile ? { width: '100%', minWidth: 0 } : {}),
                                        ...(procesando || observaciones.trim().length > 0 || !documentosObligatoriosCompletos ? styles.disabled : {})
                                    }}
                                    onClick={handleAprobar}
                                    disabled={procesando || observaciones.trim().length > 0 || !documentosObligatoriosCompletos}
                                >
                                    {procesando ? '⏳ Procesando...' : '✅ Aprobar Solicitud'}
                                </button>
                                <button
                                    style={{
                                        ...styles.btnRechazar,
                                        ...(isMobile ? { width: '100%', minWidth: 0 } : {}),
                                        ...(procesando || observaciones.trim().length === 0 || !documentosObligatoriosCompletos ? styles.disabled : {})
                                    }}
                                    onClick={handleRechazar}
                                    disabled={procesando || observaciones.trim().length === 0 || !documentosObligatoriosCompletos}
                                >
                                    {procesando ? '⏳ Procesando...' : '❌ Rechazar Solicitud'}
                                </button>
                            </div>
                        </>
                    ) : (
                        <p style={{ margin: 0 }}>{selectedSolicitud.observaciones || 'Sin observaciones registradas'}</p>
                    )}
                </div>

                <div style={styles.nota}>
                    {esPendienteSeleccionada
                        ? '📌 Si apruebas, la solicitud se envía al alcalde para firma. Si rechazas, se notificará al solicitante con la observación.'
                        : '📌 Esta solicitud ya fue procesada por verificación.'}
                </div>
            </div>
        </div>
    );
}
