import { API_AUTH_URL } from '../config/api';

export async function login(credentials) {
    const response = await fetch(`${API_AUTH_URL}/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(credentials),
    });
    if (!response.ok) {
        const error = await response.text();
        throw new Error(error || 'Credenciales inválidas');
    }
    return response.json();
}

export async function listarUsuariosOperativos(adminUsername) {
    const response = await fetch(`${API_AUTH_URL}/usuarios-operativos`, {
        headers: { 'X-Admin-Username': adminUsername },
    });
    if (!response.ok) {
        const msg = await response.text();
        throw new Error(msg || 'No se pudo cargar usuarios operativos');
    }
    return response.json();
}

export async function actualizarUsuarioOperativo(adminUsername, usuarioId, payload) {
    const response = await fetch(`${API_AUTH_URL}/usuarios-operativos/${usuarioId}`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            'X-Admin-Username': adminUsername,
        },
        body: JSON.stringify(payload),
    });
    if (!response.ok) {
        const msg = await response.text();
        throw new Error(msg || 'No se pudo actualizar el usuario');
    }
    return response.json();
}
