const hostActual = typeof window !== 'undefined' ? window.location.hostname : 'localhost';
const origenPorDefecto = `http://${hostActual}:8080`;

export const API_ORIGIN = (import.meta.env.VITE_API_ORIGIN || origenPorDefecto).replace(/\/+$/, '');
export const API_TRAMITES_URL = `${API_ORIGIN}/api/tramites`;
export const API_AUTH_URL = `${API_ORIGIN}/api/auth`;
