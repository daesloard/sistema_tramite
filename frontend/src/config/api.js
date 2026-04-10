const hostActual = typeof window !== 'undefined' ? window.location.hostname : 'localhost';
const esLocal = hostActual === 'localhost' || hostActual === '127.0.0.1';
const origenPorDefecto = esLocal
	? 'http://localhost:9090'
	: (typeof window !== 'undefined' ? window.location.origin : 'http://localhost:9090');

export const API_ORIGIN = (import.meta.env.VITE_API_ORIGIN || origenPorDefecto)
	.trim()
	.replace(/\/+$/, '');
export const API_TRAMITES_URL = `${API_ORIGIN}/api/tramites`;
export const API_AUTH_URL = `${API_ORIGIN}/api/auth`;
export const API_NOTIFICACIONES_URL = `${API_ORIGIN}/api/notificaciones`;
export const API_DOCUMENTOS_URL = `${API_ORIGIN}/api/documentacion`;
