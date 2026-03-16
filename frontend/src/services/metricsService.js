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
        postFirmaEmailErrors,
        postFirmaDuration,
        pdfErrors,
        pdfGenerationDuration
    ] = await Promise.all([
        fetchSafe(`${ACTUATOR_URL}/metrics/http.server.requests?tag=uri:/api/tramites&tag=method:GET&statistic=VALUE_AT_PERCENTILE&percentile=0.95`),
        fetchSafe(`${ACTUATOR_URL}/metrics/tramites.postfirma.total`),
        fetchSafe(`${ACTUATOR_URL}/metrics/tramites.postfirma.email.errors`),
        fetchSafe(`${ACTUATOR_URL}/metrics/tramites.postfirma.duration`),
        fetchSafe(`${ACTUATOR_URL}/metrics/tramites.pdf.generation.errors`),
        fetchSafe(`${ACTUATOR_URL}/metrics/tramites.pdf.generation.duration`),
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
            emailErrors: getVal(postFirmaEmailErrors),
            duration: getVal(postFirmaDuration),
        },
        pdf: {
            errors: getVal(pdfErrors),
            generationDuration: getVal(pdfGenerationDuration),
        }
    };
}
