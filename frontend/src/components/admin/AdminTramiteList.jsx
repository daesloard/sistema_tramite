import { Fragment } from 'react';
import AdminTramiteItem from './AdminTramiteItem';

export default function AdminTramiteList({
    tramites,
    tramitesFiltradosAdmin,
    loading,
    cargarTramites,
    busquedaAdmin,
    setBusquedaAdmin,
    tramiteExpandidoId,
    toggleDetalleTramiteAdmin,
    filasTramiteRef,
    documentStatusAdmin,
    auditoriaAdmin,
    loadingDocumentStatusAdminId,
    loadingAuditoriaAdminId,
    archivoCargaAdmin,
    subiendoDocumentoAdminKey,
    mensajeGestionAdmin,
    setMensajeGestionAdmin,
    seleccionarArchivoAdmin,
    subirDocumentoAdmin,
    abrirDocumentoAdmin,
    descargarDocumentoAdmin,
    abrirDocumentoGeneradoAdmin,
    descargarDocumentoGeneradoAdmin,
    notificarVerificadorDesdeAdmin,
    notificandoVerificadorId,
    obtenerDocumentosAdmin,
    obtenerFaltantesDocumentales,
    getEstadoBadgeStyle,
    formatoFecha,
    formatoFechaHora,
    styles
}) {
    return (
        <div style={styles.adminCard}>
            <div style={styles.seccionHeader}>
                <h2 style={{ ...styles.adminCardTitle, marginBottom: 0 }}>Solicitudes Radicadas</h2>
                <button style={styles.btnRefrescar} onClick={() => cargarTramites({ forceRefresh: true })}>
                    {loading ? 'Actualizando...' : 'Refrescar'}
                </button>
            </div>

            <div style={styles.adminBusquedaWrap}>
                <input
                    style={styles.adminBusquedaInput}
                    placeholder="Buscar por radicado, solicitante, documento, estado o tipo..."
                    value={busquedaAdmin}
                    onChange={(e) => setBusquedaAdmin(e.target.value)}
                />
                {busquedaAdmin.trim() ? (
                    <button style={styles.btnToggleSeccion} onClick={() => setBusquedaAdmin('')}>
                        Limpiar
                    </button>
                ) : null}
                <p style={styles.adminBusquedaMeta}>Mostrando {tramitesFiltradosAdmin.length} de {tramites.length}</p>
            </div>

            {loading && tramites.length === 0 && <p>Cargando...</p>}
            {!loading && tramites.length === 0 && <p>No hay solicitudes registradas.</p>}
            {!loading && tramites.length > 0 && tramitesFiltradosAdmin.length === 0 ? <p>No hay solicitudes que coincidan con la búsqueda.</p> : null}
            
            {tramitesFiltradosAdmin.length > 0 && (
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
                            {tramitesFiltradosAdmin.map((tramite) => (
                                <AdminTramiteItem
                                    key={tramite.id}
                                    tramite={tramite}
                                    expandido={tramiteExpandidoId === tramite.id}
                                    toggleDetalle={() => toggleDetalleTramiteAdmin(tramite.id)}
                                    filasRef={filasTramiteRef}
                                    documentStatus={documentStatusAdmin?.[tramite.id]}
                                    auditoriaEventos={auditoriaAdmin?.[tramite.id] || []}
                                    loadingDocumentStatus={loadingDocumentStatusAdminId === tramite.id}
                                    loadingAuditoria={loadingAuditoriaAdminId === tramite.id}
                                    archivoCarga={archivoCargaAdmin}
                                    subiendoDocumentoKey={subiendoDocumentoAdminKey}
                                    mensajeGestion={mensajeGestionAdmin}
                                    setMensajeGestion={setMensajeGestionAdmin}
                                    seleccionarArchivo={seleccionarArchivoAdmin}
                                    subirDocumento={subirDocumentoAdmin}
                                    abrirDocumento={abrirDocumentoAdmin}
                                    descargarDocumento={descargarDocumentoAdmin}
                                    abrirDocumentoGenerado={abrirDocumentoGeneradoAdmin}
                                    descargarDocumentoGenerado={descargarDocumentoGeneradoAdmin}
                                    notificarVerificador={notificarVerificadorDesdeAdmin}
                                    notificandoVerificador={notificandoVerificadorId === tramite.id}
                                    obtenerDocumentos={obtenerDocumentosAdmin}
                                    obtenerFaltantesDocumentales={obtenerFaltantesDocumentales}
                                    getEstadoBadgeStyle={getEstadoBadgeStyle}
                                    formatoFecha={formatoFecha}
                                    formatoFechaHora={formatoFechaHora}
                                    styles={styles}
                                />
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}
