export default function VerificadorTramiteList({
    filtroActual,
    solicitudesFiltradasBusqueda,
    solicitudesFiltradas,
    busquedaSolicitudes,
    setBusquedaSolicitudes,
    selectedSolicitud,
    setSelectedSolicitud,
    setConsecutivo,
    setObservaciones,
    isMobile,
    estadoBadge,
    formatearFechaHora,
    obtenerTextoDiasHabilesRestantes,
    styles
}) {
    return (
        <div style={{ ...styles.lista, ...(isMobile ? {} : styles.listaDesktop) }}>
            <h3 style={styles.listaTitulo}>{filtroActual.titulo} ({solicitudesFiltradasBusqueda.length})</h3>

            <div style={styles.busquedaWrap}>
                <input
                    style={styles.busquedaInput}
                    placeholder="Buscar por radicado, solicitante, documento, estado o tipo..."
                    value={busquedaSolicitudes}
                    onChange={(e) => setBusquedaSolicitudes(e.target.value)}
                />
                {busquedaSolicitudes.trim() && (
                    <button style={styles.busquedaBtn} onClick={() => setBusquedaSolicitudes('')}>
                        Limpiar
                    </button>
                )}
                <p style={styles.busquedaMeta}>Mostrando {solicitudesFiltradasBusqueda.length} de {solicitudesFiltradas.length}</p>
            </div>

            {solicitudesFiltradas.length === 0 ? (
                <div style={styles.listaVacia}>No hay solicitudes para esta vista</div>
            ) : solicitudesFiltradasBusqueda.length === 0 ? (
                <div style={styles.listaVacia}>No hay solicitudes que coincidan con la búsqueda</div>
            ) : (
                <div style={{ ...styles.listaScroll, ...(isMobile ? styles.listaScrollMobile : styles.listaScrollDesktop) }}>
                    {solicitudesFiltradasBusqueda.map((solicitud) => {
                        const badge = estadoBadge(solicitud.estado);
                        const esPendiente = solicitud.estado === 'EN_VALIDACION' || solicitud.estado === 'RADICADO';
                        const textoDiasHabiles = esPendiente
                            ? obtenerTextoDiasHabilesRestantes(solicitud.fechaVencimiento)
                            : null;
                        return (
                            <div
                                key={solicitud.id}
                                style={{ ...styles.item, ...(selectedSolicitud?.id === solicitud.id ? styles.itemActivo : {}) }}
                                onClick={() => {
                                    setSelectedSolicitud(solicitud);
                                    setConsecutivo((solicitud.consecutivoVerificador || '').toString().replace(/\D/g, '').slice(0, 6));
                                    setObservaciones(solicitud.observaciones || '');
                                }}
                            >
                                <div style={styles.row}>
                                    <span style={styles.radicado}>{solicitud.numeroRadicado}</span>
                                    <span style={{ ...styles.badge, ...badge.style }}>{badge.text}</span>
                                </div>
                                <p style={styles.itemP}><strong>{solicitud.nombreSolicitante}</strong></p>
                                <p style={styles.itemP}>{solicitud.numeroDocumento}</p>
                                <p style={{ ...styles.itemP, color: '#95a5a6', fontSize: '12px' }}>
                                    Radicado: {formatearFechaHora(solicitud.fechaRadicacion)}
                                </p>
                                {textoDiasHabiles && (
                                    <p style={{ ...styles.itemP, color: textoDiasHabiles.startsWith('⚠️') ? '#b45309' : '#2563eb', fontSize: '12px', fontWeight: 600 }}>
                                        {textoDiasHabiles}
                                    </p>
                                )}
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
}
