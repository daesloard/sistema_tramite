export default function AdminCertificates({
    certificadosExpandido,
    setCertificadosExpandido,
    filtroCertRadicado,
    setFiltroCertRadicado,
    filtroCertNombre,
    setFiltroCertNombre,
    filtroCertTipo,
    setFiltroCertTipo,
    certificadosGeneradosFiltrados,
    formatoFecha,
    styles
}) {
    return (
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

            {certificadosExpandido && (
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
            )}
        </div>
    );
}
