export default function VerificadorHeader({
    filtros,
    filtroActual,
    setFiltroActual,
    handleConsolidado,
    cargandoConsolidado,
    styles
}) {
    return (
        <div style={styles.header}>
            <div style={styles.seccionHeader}>
                <div style={styles.filtros}>
                    {filtros.map((f) => (
                        <button
                            key={f.key}
                            style={{ ...styles.btnFiltro, ...(filtroActual.key === f.key ? styles.btnFiltroActivo : {}) }}
                            onClick={() => setFiltroActual(f)}
                        >
                            {f.titulo}
                        </button>
                    ))}
                </div>
            </div>
            <button
                style={{ ...styles.btnConsolidado, ...(cargandoConsolidado ? styles.disabled : {}) }}
                onClick={handleConsolidado}
                disabled={cargandoConsolidado}
            >
                {cargandoConsolidado ? '⏳ Generando Excel...' : '📊 Descargar Excel Consolidado'}
            </button>
        </div>
    );
}
