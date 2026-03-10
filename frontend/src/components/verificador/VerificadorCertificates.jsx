export default function VerificadorCertificates({
    certificadosExpandido,
    setCertificadosExpandido,
    filtroCertRadicado,
    setFiltroCertRadicado,
    filtroCertNombre,
    setFiltroCertNombre,
    filtroCertTipo,
    setFiltroCertTipo,
    certificadosGeneradosFiltrados,
    formatearFechaHora,
    styles
}) {
    return (
        <div style={styles.certificadosSeccion}>
            <div style={styles.seccionHeader}>
                <h3 style={{ ...styles.listaTitulo, margin: 0 }}>📄 Certificados Generados ({certificadosGeneradosFiltrados.length})</h3>
                <button style={styles.btnToggleSeccion} onClick={() => setCertificadosExpandido((prev) => !prev)}>
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
                        <div style={styles.listaVacia}>No hay certificados generados para los filtros seleccionados</div>
                    ) : (
                        <div style={styles.listaCertificados}>
                            {certificadosGeneradosFiltrados.map((cert) => (
                                <div key={`cert-${cert.id}`} style={styles.certItem}>
                                    <p style={styles.certMeta}><strong>{cert.numeroRadicado}</strong> · {cert.nombreSolicitante}</p>
                                    <p style={styles.certMeta}>Respuesta: {cert.estado === 'FINALIZADO' ? 'Positiva' : 'Negativa'} · Tipo: {cert.tipo_certificado || 'Certificado'}</p>
                                    <p style={styles.certMeta}>Fecha: {formatearFechaHora(cert.fechaVerificacion || cert.fechaFirmaAlcalde || cert.fechaRadicacion)}</p>
                                </div>
                            ))}
                        </div>
                    )}
                </>
            )}
        </div>
    );
}
