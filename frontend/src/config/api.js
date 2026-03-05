const hostActual = typeof window !== 'undefined' ? window.location.hostname : 'localhost';
const esDeployPages = hostActual.endsWith('pages.dev');
const origenPorDefecto = esDeployPages
	? 'https://sistema-tramite.onrender.com'
	: `http://${hostActual}:8080`;

export const API_ORIGIN = (import.meta.env.VITE_API_ORIGIN || origenPorDefecto)
	.trim()
	.replace(/\/+$/, '');
export const API_TRAMITES_URL = `${API_ORIGIN}/api/tramites`;
export const API_AUTH_URL = `${API_ORIGIN}/api/auth`;
export const API_NOTIFICACIONES_URL = `${API_ORIGIN}/api/notificaciones`;
