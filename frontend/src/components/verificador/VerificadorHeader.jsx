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
            <div style={styles.tabContainer}>
                {filtros.map((f) => (
                    <button
                        key={f.id}
                        style={{ ...styles.tab, ...(filtroActual.id === f.id ? styles.tabActivo : {}) }}
                        onClick={() => setFiltroActual(f)}
                    >
                        {f.titulo}
                    </button>
                ))}
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
