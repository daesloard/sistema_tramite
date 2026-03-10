import { API_ORIGIN } from '../config/api';

const ACTUATOR_URL = `${API_ORIGIN}/actuator`;

async function fetchSafe(url) {
    try {
        const response = await fetch(url);
        if (!response.ok) return null;
        return response.json();
    } catch {
        return null;
    }
}

export async function obtenerMetricasOperativasAdmin() {
    const health = await fetchSafe(`${ACTUATOR_URL}/health`);
    if (!health) {
        return { available: false, capturedAt: new Date().toISOString() };
    }

    const [
        httpP95,
        postFirmaTotal,
        postFirmaSuccess,
        postFirmaException,
        postFirmaEmailErrors,
        pdfTotal,
        pdfSuccess,
        pdfErrors,
        pdfAvg,
        pdfMax,
        pdfEngineGotenberg,
        pdfEngineLibreoffice,
        pdfEngineDocx4j
    ] = await Promise.all([
        fetchSafe(`${ACTUATOR_URL}/metrics/http.server.requests?tag=uri:/api/tramites&tag=method:GET&statistic=VALUE_AT_PERCENTILE&percentile=0.95`),
        fetchSafe(`${ACTUATOR_URL}/metrics/tramites.postfirma.total`),
        fetchSafe(`${ACTUATOR_URL}/metrics/tramites.postfirma.success`),
        fetchSafe(`${ACTUATOR_URL}/metrics/tramites.postfirma.exception`),
        fetchSafe(`${ACTUATOR_URL}/metrics/tramites.postfirma.email.errors`),
        fetchSafe(`${ACTUATOR_URL}/metrics/tramites.pdf.total`),
        fetchSafe(`${ACTUATOR_URL}/metrics/tramites.pdf.success`),
        fetchSafe(`${ACTUATOR_URL}/metrics/tramites.pdf.errors`),
        fetchSafe(`${ACTUATOR_URL}/metrics/tramites.pdf.duration?statistic=AVG`),
        fetchSafe(`${ACTUATOR_URL}/metrics/tramites.pdf.duration?statistic=MAX`),
        fetchSafe(`${ACTUATOR_URL}/metrics/tramites.pdf.total?tag=engine:gotenberg`),
        fetchSafe(`${ACTUATOR_URL}/metrics/tramites.pdf.total?tag=engine:libreoffice`),
        fetchSafe(`${ACTUATOR_URL}/metrics/tramites.pdf.total?tag=engine:docx4j`),
    ]);

    const getVal = (m) => (m?.measurements?.[0]?.value ?? 0);

    return {
        available: true,
        capturedAt: new Date().toISOString(),
        http: {
            p95Seconds: getVal(httpP95),
        },
        postFirma: {
            total: getVal(postFirmaTotal),
            success: getVal(postFirmaSuccess),
            exception: getVal(postFirmaException),
            emailErrors: getVal(postFirmaEmailErrors),
            successAvgSeconds: 0, // No hay métrica directa de promedio por ahora
            successMaxSeconds: 0,
        },
        pdf: {
            total: getVal(pdfTotal),
            success: getVal(pdfSuccess),
            errors: getVal(pdfErrors),
            avgSeconds: getVal(pdfAvg),
            maxSeconds: getVal(pdfMax),
            engineGotenberg: getVal(pdfEngineGotenberg),
            engineLibreoffice: getVal(pdfEngineLibreoffice),
            engineDocx4j: getVal(pdfEngineDocx4j),
        }
    };
}
