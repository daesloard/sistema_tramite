import { API_NOTIFICACIONES_URL } from '../config/api';

const VAPID_CACHE_KEY = 'vapid_public_key_v1';

export async function obtenerWebPushPublicKey() {
    try {
        const cached = localStorage.getItem(VAPID_CACHE_KEY);
        if (cached) return JSON.parse(cached);

        const response = await fetch(`${API_NOTIFICACIONES_URL}/webpush/public-key`);
        if (!response.ok) throw new Error('No se pudo obtener la llave pública VAPID');
        const data = await response.json();
        localStorage.setItem(VAPID_CACHE_KEY, JSON.stringify(data));
        return data;
    } catch {
        return null;
    }
}

export async function registrarWebPushSuscripcion({ username, subscription }) {
    const response = await fetch(`${API_NOTIFICACIONES_URL}/webpush/subscribe`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Username': username,
        },
        body: JSON.stringify(subscription),
    });
    if (!response.ok) throw new Error('No se pudo registrar la suscripción Web Push');
    return response.json();
}

export async function desactivarWebPushSuscripcion({ username, endpoint }) {
    const response = await fetch(`${API_NOTIFICACIONES_URL}/webpush/subscribe`, {
        method: 'DELETE',
        headers: {
            'Content-Type': 'application/json',
            'X-Username': username,
        },
        body: JSON.stringify({ endpoint }),
    });
    if (!response.ok) throw new Error('No se pudo desactivar la suscripción Web Push');
    return response.json();
}

export async function listarMisNotificaciones(username) {
    const response = await fetch(`${API_NOTIFICACIONES_URL}/mis`, {
        headers: { 'X-Username': username },
    });
    if (!response.ok) throw new Error('No se pudieron cargar las notificaciones');
    return response.json();
}

export async function marcarNotificacionLeida(id, username) {
    const response = await fetch(`${API_NOTIFICACIONES_URL}/${id}/leer`, {
        method: 'POST',
        headers: { 'X-Username': username },
    });
    if (!response.ok) throw new Error('No se pudo marcar como leída');
    return response.json();
}

export async function marcarTodasLeidas(username) {
    const response = await fetch(`${API_NOTIFICACIONES_URL}/leer-todas`, {
        method: 'POST',
        headers: { 'X-Username': username },
    });
    if (!response.ok) throw new Error('No se pudieron marcar todas como leídas');
    return response.json();
}
