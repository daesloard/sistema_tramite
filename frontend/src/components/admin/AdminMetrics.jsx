import { useMemo } from 'react';
import { formatearFechaHora } from '../../utils/dateUtils';

const UMBRALES_OPERACION = {
    httpP95WarningSeconds: 1.2,
    httpP95CriticalSeconds: 5.0,
    postFirmaExceptionRatioWarning: 0.03,
    postFirmaExceptionRatioCritical: 0.1,
    emailErrorsWarning: 1,
    emailErrorsCritical: 5,
    pdfErrorsWarning: 1,
    pdfErrorsCritical: 5,
    pdfAvgWarningSeconds: 8,
    pdfAvgCriticalSeconds: 15,
};

const formatoNumeroMetrica = (valor, decimales = 0) => {
    const numero = Number(valor);
    if (!Number.isFinite(numero)) return '-';
    return numero.toLocaleString('es-CO', {
        minimumFractionDigits: decimales,
        maximumFractionDigits: decimales,
    });
};

const formatoDuracion = (valorSegundos) => {
    const valor = Number(valorSegundos);
    if (!Number.isFinite(valor)) return '-';
    if (valor < 1) {
        return `${Math.round(valor * 1000)} ms`;
    }
    return `${valor.toFixed(2)} s`;
};

const etiquetaEstadoAlerta = (estado) => {
    if (estado === 'critical') return 'CRITICO';
    if (estado === 'warning') return 'ADVERTENCIA';
    if (estado === 'ok') return 'OK';
    return 'INFO';
};

const estiloEstadoAlerta = (estado) => {
    if (estado === 'critical') return { background: '#fee2e2', color: '#b91c1c' };
    if (estado === 'warning') return { background: '#ffedd5', color: '#c2410c' };
    if (estado === 'ok') return { background: '#dcfce7', color: '#15803d' };
    return { background: '#e0f2fe', color: '#0369a1' };
};

const calcularAlertasOperativas = (metricas) => {
    if (!metricas || metricas.available === false) return [];

    const alertas = [];

    const p95 = Number(metricas?.http?.p95Seconds);
    if (!Number.isFinite(p95)) {
        alertas.push({ id: 'http-p95', titulo: 'Latencia HTTP p95', estado: 'info', detalle: 'Sin datos suficientes para calcular p95.' });
    } else if (p95 >= UMBRALES_OPERACION.httpP95CriticalSeconds) {
        alertas.push({ id: 'http-p95', titulo: 'Latencia HTTP p95', estado: 'critical', detalle: `p95 en ${formatoDuracion(p95)} (umbral critico >= ${formatoDuracion(UMBRALES_OPERACION.httpP95CriticalSeconds)}).` });
    } else if (p95 >= UMBRALES_OPERACION.httpP95WarningSeconds) {
        alertas.push({ id: 'http-p95', titulo: 'Latencia HTTP p95', estado: 'warning', detalle: `p95 en ${formatoDuracion(p95)} (umbral advertencia >= ${formatoDuracion(UMBRALES_OPERACION.httpP95WarningSeconds)}).` });
    } else {
        alertas.push({ id: 'http-p95', titulo: 'Latencia HTTP p95', estado: 'ok', detalle: `p95 en ${formatoDuracion(p95)} dentro de rango.` });
    }

    const postFirmaTotal = Number(metricas?.postFirma?.total) || 0;
    const postFirmaException = Number(metricas?.postFirma?.exception) || 0;
    if (postFirmaTotal <= 0) {
        alertas.push({ id: 'postfirma-exception', titulo: 'Excepciones post-firma', estado: 'info', detalle: 'Aun no hay ejecuciones post-firma para calcular tasa.' });
    } else {
        const ratio = postFirmaException / postFirmaTotal;
        if (ratio >= UMBRALES_OPERACION.postFirmaExceptionRatioCritical) {
            alertas.push({ id: 'postfirma-exception', titulo: 'Excepciones post-firma', estado: 'critical', detalle: `Tasa ${formatoNumeroMetrica(ratio * 100, 1)}% (${postFirmaException}/${postFirmaTotal}).` });
        } else if (ratio >= UMBRALES_OPERACION.postFirmaExceptionRatioWarning) {
            alertas.push({ id: 'postfirma-exception', titulo: 'Excepciones post-firma', estado: 'warning', detalle: `Tasa ${formatoNumeroMetrica(ratio * 100, 1)}% (${postFirmaException}/${postFirmaTotal}).` });
        } else {
            alertas.push({ id: 'postfirma-exception', titulo: 'Excepciones post-firma', estado: 'ok', detalle: `Tasa ${formatoNumeroMetrica(ratio * 100, 1)}% (${postFirmaException}/${postFirmaTotal}).` });
        }
    }

    const emailErrors = Number(metricas?.postFirma?.emailErrors);
    if (!Number.isFinite(emailErrors)) {
        alertas.push({ id: 'email-errors', titulo: 'Errores de correo', estado: 'info', detalle: 'Metrica de errores de correo aun no inicializada.' });
    } else if (emailErrors >= UMBRALES_OPERACION.emailErrorsCritical) {
        alertas.push({ id: 'email-errors', titulo: 'Errores de correo', estado: 'critical', detalle: `${formatoNumeroMetrica(emailErrors)} errores acumulados.` });
    } else if (emailErrors >= UMBRALES_OPERACION.emailErrorsWarning) {
        alertas.push({ id: 'email-errors', titulo: 'Errores de correo', estado: 'warning', detalle: `${formatoNumeroMetrica(emailErrors)} error(es) acumulado(s).` });
    } else {
        alertas.push({ id: 'email-errors', titulo: 'Errores de correo', estado: 'ok', detalle: 'Sin errores de correo registrados.' });
    }

    const pdfErrors = Number(metricas?.pdf?.errors);
    if (!Number.isFinite(pdfErrors)) {
        alertas.push({ id: 'pdf-errors', titulo: 'Errores de generacion PDF', estado: 'info', detalle: 'Metrica PDF aun no inicializada.' });
    } else if (pdfErrors >= UMBRALES_OPERACION.pdfErrorsCritical) {
        alertas.push({ id: 'pdf-errors', titulo: 'Errores de generacion PDF', estado: 'critical', detalle: `${formatoNumeroMetrica(pdfErrors)} errores acumulados de PDF.` });
    } else if (pdfErrors >= UMBRALES_OPERACION.pdfErrorsWarning) {
        alertas.push({ id: 'pdf-errors', titulo: 'Errores de generacion PDF', estado: 'warning', detalle: `${formatoNumeroMetrica(pdfErrors)} error(es) acumulado(s) de PDF.` });
    } else {
        alertas.push({ id: 'pdf-errors', titulo: 'Errores de generacion PDF', estado: 'ok', detalle: 'Sin errores de PDF registrados.' });
    }

    const pdfAvg = Number(metricas?.pdf?.avgSeconds);
    if (!Number.isFinite(pdfAvg)) {
        alertas.push({ id: 'pdf-avg', titulo: 'Tiempo promedio PDF', estado: 'info', detalle: 'Sin datos suficientes de tiempo promedio PDF.' });
    } else if (pdfAvg >= UMBRALES_OPERACION.pdfAvgCriticalSeconds) {
        alertas.push({ id: 'pdf-avg', titulo: 'Tiempo promedio PDF', estado: 'critical', detalle: `Promedio ${formatoDuracion(pdfAvg)} por PDF.` });
    } else if (pdfAvg >= UMBRALES_OPERACION.pdfAvgWarningSeconds) {
        alertas.push({ id: 'pdf-avg', titulo: 'Tiempo promedio PDF', estado: 'warning', detalle: `Promedio ${formatoDuracion(pdfAvg)} por PDF.` });
    } else {
        alertas.push({ id: 'pdf-avg', titulo: 'Tiempo promedio PDF', estado: 'ok', detalle: `Promedio ${formatoDuracion(pdfAvg)} por PDF.` });
    }

    return alertas;
};

export default function AdminMetrics({ 
    metricasOperativas, 
    loadingMetricas, 
    errorMetricas, 
    cargarMetricasOperativas, 
    expandido, 
    setExpandido, 
    styles 
}) {
    const alertasOperativas = useMemo(() => calcularAlertasOperativas(metricasOperativas), [metricasOperativas]);

    const estadoGeneralOperativo = useMemo(() => {
        if (!metricasOperativas || metricasOperativas.available === false) return 'info';
        if (alertasOperativas.some((item) => item.estado === 'critical')) return 'critical';
        if (alertasOperativas.some((item) => item.estado === 'warning')) return 'warning';
        if (alertasOperativas.some((item) => item.estado === 'ok')) return 'ok';
        return 'info';
    }, [metricasOperativas, alertasOperativas]);

    return (
        <div style={styles.adminCard}>
            <div style={styles.seccionHeader}>
                <h2 style={{ ...styles.adminCardTitle, marginBottom: 0 }}>Metricas Operativas</h2>
                <div style={styles.metricasAcciones}>
                    <button
                        style={{ ...styles.btnRefrescar, ...(loadingMetricas ? styles.btnGuardarUsuarioDisabled : {}) }}
                        onClick={cargarMetricasOperativas}
                        disabled={loadingMetricas}
                    >
                        {loadingMetricas ? 'Actualizando...' : 'Refrescar'}
                    </button>
                    <button
                        style={styles.btnToggleSeccion}
                        onClick={() => setExpandido((prev) => !prev)}
                    >
                        {expandido ? 'Ocultar' : 'Mostrar'}
                    </button>
                </div>
            </div>

            {expandido && (
                <>
                    <p style={styles.adminNota}>
                        Fuente: Actuator. Ultima lectura: {metricasOperativas?.capturedAt ? formatearFechaHora(metricasOperativas.capturedAt) : '-'}
                    </p>
                    {errorMetricas && <p style={styles.metricaError}>{errorMetricas}</p>}
                    {loadingMetricas && !metricasOperativas && <p>Cargando metricas...</p>}

                    {metricasOperativas && metricasOperativas?.available !== false && (
                        <>
                            <div style={styles.metricasEstadoWrap}>
                                <p style={styles.metricasEstadoTitulo}>Estado operativo general</p>
                                <span style={{ ...styles.metricasEstadoChip, ...estiloEstadoAlerta(estadoGeneralOperativo) }}>
                                    {etiquetaEstadoAlerta(estadoGeneralOperativo)}
                                </span>
                            </div>

                            {alertasOperativas.length > 0 && (
                                <div style={styles.alertasOperativasLista}>
                                    {alertasOperativas.map((alerta) => (
                                        <div key={alerta.id} style={styles.alertaOperativaItem}>
                                            <p style={styles.alertaOperativaTitulo}>
                                                <span style={{ ...styles.alertaOperativaChip, ...estiloEstadoAlerta(alerta.estado) }}>
                                                    {etiquetaEstadoAlerta(alerta.estado)}
                                                </span>
                                                {alerta.titulo}
                                            </p>
                                            <p style={styles.alertaOperativaDetalle}>{alerta.detalle}</p>
                                        </div>
                                    ))}
                                </div>
                            )}

                            <div style={styles.metricasGrid}>
                                <div style={styles.metricaCard}>
                                    <p style={styles.metricaTitulo}>Post-firma total</p>
                                    <p style={styles.metricaValor}>{formatoNumeroMetrica(metricasOperativas?.postFirma?.total)}</p>
                                </div>

                                <div style={styles.metricaCard}>
                                    <p style={styles.metricaTitulo}>Errores de correo</p>
                                    <p style={styles.metricaValor}>{formatoNumeroMetrica(metricasOperativas?.postFirma?.emailErrors)}</p>
                                    <p style={styles.metricaDetalle}>Métrica: tramites.postfirma.email.errors</p>
                                </div>

                                <div style={styles.metricaCard}>
                                    <p style={styles.metricaTitulo}>Duración post-firma</p>
                                    <p style={styles.metricaValor}>{formatoDuracion(metricasOperativas?.postFirma?.duration)}</p>
                                    <p style={styles.metricaDetalle}>Métrica: tramites.postfirma.duration</p>
                                </div>

                                <div style={styles.metricaCard}>
                                    <p style={styles.metricaTitulo}>Errores generación PDF</p>
                                    <p style={styles.metricaValor}>{formatoNumeroMetrica(metricasOperativas?.pdf?.errors)}</p>
                                    <p style={styles.metricaDetalle}>Métrica: tramites.pdf.generation.errors</p>
                                </div>

                                <div style={styles.metricaCard}>
                                    <p style={styles.metricaTitulo}>Duración generación PDF</p>
                                    <p style={styles.metricaValor}>{formatoDuracion(metricasOperativas?.pdf?.generationDuration)}</p>
                                    <p style={styles.metricaDetalle}>Métrica: tramites.pdf.generation.duration</p>
                                </div>
                            </div>
                        </>
                    )}
                </>
            )}
        </div>
    );
}
