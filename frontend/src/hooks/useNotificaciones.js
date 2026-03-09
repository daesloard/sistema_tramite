import { useState, useEffect, useCallback, useRef } from 'react';
import { API_NOTIFICACIONES_URL } from '../config/api';
import { desactivarWebPushSuscripcion, obtenerWebPushPublicKey, registrarWebPushSuscripcion } from '../services/api';

const POLL_NOTIFICACIONES_CERRADO_MS = 60000;
const POLL_NOTIFICACIONES_ABIERTO_MS = 20000;
const MIN_GAP_CARGA_NOTIFICACIONES_MS = 8000;

export const soportaNotificacionesNavegador = () => (
  typeof window !== 'undefined' && 'Notification' in window
);

export const soportaWebPushNativo = () => (
  typeof window !== 'undefined'
  && typeof navigator !== 'undefined'
  && 'serviceWorker' in navigator
  && 'PushManager' in window
);

const urlBase64ToUint8Array = (base64String) => {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding)
    .replace(/-/g, '+')
    .replace(/_/g, '/');
  const rawData = window.atob(base64);
  const outputArray = new Uint8Array(rawData.length);
  for (let i = 0; i < rawData.length; i += 1) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
};

const keyPreferenciaNotificacionesNavegador = (username) => `notificaciones_navegador_${username || 'anon'}`;

export function useNotificaciones(usuarioActual, enPanel) {
  const [notificacionesUsuario, setNotificacionesUsuario] = useState([]);
  const [noLeidasUsuario, setNoLeidasUsuario] = useState(0);
  const [notificacionesAbiertas, setNotificacionesAbiertas] = useState(false);
  const [cargandoNotificaciones, setCargandoNotificaciones] = useState(false);
  const [notificacionesNavegadorHabilitadas, setNotificacionesNavegadorHabilitadas] = useState(false);
  const [webPushSuscrito, setWebPushSuscrito] = useState(false);

  const notificacionesEnVueloRef = useRef(false);
  const ultimaCargaNotificacionesRef = useRef(0);
  const trackingNotificacionesRef = useRef({ username: null, initialized: false, seenIds: new Set() });

  const obtenerHeadersNotificaciones = useCallback(() => {
    if (!usuarioActual?.username) return {};
    return { 'X-Username': usuarioActual.username };
  }, [usuarioActual?.username]);

  useEffect(() => {
    if (!soportaWebPushNativo()) return;
    const onWorkerMessage = (event) => {
      if (event?.data?.type === 'OPEN_NOTIFICATIONS_CENTER') {
        setNotificacionesAbiertas(true);
      }
    };
    navigator.serviceWorker.addEventListener('message', onWorkerMessage);
    return () => navigator.serviceWorker.removeEventListener('message', onWorkerMessage);
  }, []);

  useEffect(() => {
    if (!soportaNotificacionesNavegador() || !usuarioActual?.username) {
      setNotificacionesNavegadorHabilitadas(false);
      setWebPushSuscrito(false);
      trackingNotificacionesRef.current = { username: usuarioActual?.username || null, initialized: false, seenIds: new Set() };
      return;
    }
    const key = keyPreferenciaNotificacionesNavegador(usuarioActual.username);
    const preferenciaGuardada = localStorage.getItem(key) === 'true';
    const permisoConcedido = Notification.permission === 'granted';
    setNotificacionesNavegadorHabilitadas(preferenciaGuardada && permisoConcedido);
    trackingNotificacionesRef.current = { username: usuarioActual.username, initialized: false, seenIds: new Set() };
  }, [usuarioActual?.username]);

  const lanzarNotificacionNavegador = useCallback((notificacion) => {
    if (!soportaNotificacionesNavegador() || Notification.permission !== 'granted') return;
    const titulo = (notificacion?.titulo || 'Nueva notificación').toString();
    const mensaje = (notificacion?.mensaje || 'Tienes una actualización en el sistema').toString();
    const id = notificacion?.id || Date.now();
    const instancia = new Notification(titulo, {
      body: mensaje,
      tag: `tramite-noti-${id}`,
      icon: '/escudo.png',
      badge: '/escudo.png',
    });
    instancia.onclick = () => {
      if (typeof window !== 'undefined') window.focus();
      setNotificacionesAbiertas(true);
    };
  }, []);

  const procesarNotificacionesNavegador = useCallback((listaNotificaciones) => {
    if (!notificacionesNavegadorHabilitadas || !usuarioActual?.username) return;
    if (!soportaNotificacionesNavegador() || Notification.permission !== 'granted') return;
    if (webPushSuscrito) return;

    const tracking = trackingNotificacionesRef.current;
    if (tracking.username !== usuarioActual.username) {
      trackingNotificacionesRef.current = { username: usuarioActual.username, initialized: false, seenIds: new Set() };
    }
    const actual = trackingNotificacionesRef.current;
    const noLeidas = (Array.isArray(listaNotificaciones) ? listaNotificaciones : []).filter((item) => !item?.leida && item?.id != null);

    if (!actual.initialized) {
      noLeidas.forEach((item) => actual.seenIds.add(item.id));
      actual.initialized = true;
      return;
    }
    const nuevas = noLeidas.filter((item) => !actual.seenIds.has(item.id));
    nuevas.forEach((item) => {
      actual.seenIds.add(item.id);
      lanzarNotificacionNavegador(item);
    });
  }, [lanzarNotificacionNavegador, notificacionesNavegadorHabilitadas, usuarioActual?.username, webPushSuscrito]);

  const cargarNotificacionesUsuario = useCallback(async ({ silenciosa = false, forzar = false } = {}) => {
    if (!usuarioActual?.username) {
      setNotificacionesUsuario([]);
      setNoLeidasUsuario(0);
      return;
    }
    const ahora = Date.now();
    const ultimaCarga = ultimaCargaNotificacionesRef.current;
    if (!forzar && (ahora - ultimaCarga) < MIN_GAP_CARGA_NOTIFICACIONES_MS) return;
    if (notificacionesEnVueloRef.current) return;

    notificacionesEnVueloRef.current = true;
    ultimaCargaNotificacionesRef.current = ahora;
    if (!silenciosa) setCargandoNotificaciones(true);

    try {
      const response = await fetch(`${API_NOTIFICACIONES_URL}/mis`, { headers: obtenerHeadersNotificaciones() });
      if (!response.ok) throw new Error('No se pudo cargar el centro de notificaciones');
      const data = await response.json();
      const lista = Array.isArray(data?.notificaciones) ? data.notificaciones : [];
      setNotificacionesUsuario(lista);
      setNoLeidasUsuario(Number(data?.noLeidas || 0));
      procesarNotificacionesNavegador(lista);
    } catch (err) {
      if (!silenciosa) console.error(err.message || 'No se pudieron cargar notificaciones');
    } finally {
      notificacionesEnVueloRef.current = false;
      if (!silenciosa) setCargandoNotificaciones(false);
    }
  }, [usuarioActual?.username, obtenerHeadersNotificaciones, procesarNotificacionesNavegador]);

  const sincronizarSuscripcionWebPush = useCallback(async () => {
    if (!usuarioActual?.username || !soportaWebPushNativo()) return false;
    try {
      const config = await obtenerWebPushPublicKey();
      if (!config?.enabled || !config?.publicKey) return false;
      await navigator.serviceWorker.register('/sw-notificaciones.js');
      const registration = await navigator.serviceWorker.ready;
      let subscription = await registration.pushManager.getSubscription();
      if (!subscription) {
        subscription = await registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: urlBase64ToUint8Array(config.publicKey),
        });
      }
      await registrarWebPushSuscripcion({ username: usuarioActual.username, subscription: subscription.toJSON() });
      return true;
    } catch (err) {
      console.warn('No se pudo sincronizar suscripción Web Push:', err?.message || err);
      return false;
    }
  }, [usuarioActual?.username]);

  const desactivarSuscripcionWebPushLocal = useCallback(async () => {
    if (!usuarioActual?.username || !soportaWebPushNativo()) return;
    try {
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.getSubscription();
      if (subscription?.endpoint) {
        try {
          await desactivarWebPushSuscripcion({ username: usuarioActual.username, endpoint: subscription.endpoint });
        } catch (err) {
          console.warn('No se pudo informar baja de suscripción en backend:', err?.message || err);
        }
        await subscription.unsubscribe();
      }
    } catch (err) {
      console.warn('No se pudo desactivar suscripción Web Push local:', err?.message || err);
    }
  }, [usuarioActual?.username]);

  useEffect(() => {
    if (!notificacionesNavegadorHabilitadas) {
      setWebPushSuscrito(false);
      return;
    }
    let activa = true;
    const sincronizar = async () => {
      const ok = await sincronizarSuscripcionWebPush();
      if (activa) setWebPushSuscrito(ok);
    };
    sincronizar();
    return () => { activa = false; };
  }, [notificacionesNavegadorHabilitadas, sincronizarSuscripcionWebPush]);

  const toggleNotificacionesNavegador = useCallback(async () => {
    if (!usuarioActual?.username || !soportaNotificacionesNavegador()) return;
    const key = keyPreferenciaNotificacionesNavegador(usuarioActual.username);

    if (notificacionesNavegadorHabilitadas) {
      setNotificacionesNavegadorHabilitadas(false);
      setWebPushSuscrito(false);
      localStorage.setItem(key, 'false');
      await desactivarSuscripcionWebPushLocal();
      return;
    }
    if (Notification.permission === 'denied') {
      console.warn('Notificaciones bloqueadas');
      localStorage.setItem(key, 'false');
      return;
    }
    if (Notification.permission !== 'granted') {
      const permiso = await Notification.requestPermission();
      if (permiso !== 'granted') {
        localStorage.setItem(key, 'false');
        return;
      }
    }
    trackingNotificacionesRef.current = { username: usuarioActual.username, initialized: false, seenIds: new Set() };
    setNotificacionesNavegadorHabilitadas(true);
    localStorage.setItem(key, 'true');
    const suscrito = await sincronizarSuscripcionWebPush();
    setWebPushSuscrito(suscrito);
    await cargarNotificacionesUsuario({ silenciosa: true, forzar: true });
  }, [cargarNotificacionesUsuario, desactivarSuscripcionWebPushLocal, notificacionesNavegadorHabilitadas, sincronizarSuscripcionWebPush, usuarioActual?.username]);

  const abrirCentroNotificaciones = async () => {
    setNotificacionesAbiertas(true);
    await cargarNotificacionesUsuario({ forzar: true });
  };

  const cerrarCentroNotificaciones = () => setNotificacionesAbiertas(false);

  const marcarNotificacionLeida = useCallback(async (idNotificacion) => {
    if (!idNotificacion) return;
    try {
      const response = await fetch(`${API_NOTIFICACIONES_URL}/${idNotificacion}/leer`, { method: 'POST', headers: obtenerHeadersNotificaciones() });
      if (!response.ok) throw new Error('No se pudo marcar la notificación');
      setNotificacionesUsuario((prev) => prev.map((item) => (item.id === idNotificacion ? { ...item, leida: true } : item)));
      setNoLeidasUsuario((prev) => Math.max(0, prev - 1));
    } catch (err) {
      console.error(err.message || 'Error al marcar notificación');
    }
  }, [obtenerHeadersNotificaciones]);

  const marcarTodasNotificacionesLeidas = useCallback(async () => {
    try {
      const response = await fetch(`${API_NOTIFICACIONES_URL}/leer-todas`, { method: 'POST', headers: obtenerHeadersNotificaciones() });
      if (!response.ok) throw new Error('No se pudieron marcar todas');
      setNotificacionesUsuario((prev) => prev.map((item) => ({ ...item, leida: true })));
      setNoLeidasUsuario(0);
    } catch (err) {
      console.error(err.message || 'Error al marcar notificaciones');
    }
  }, [obtenerHeadersNotificaciones]);

  useEffect(() => {
    if (!(enPanel && usuarioActual?.username)) return;
    const cargarSiVisible = () => {
      if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return;
      cargarNotificacionesUsuario({ silenciosa: true });
    };
    cargarSiVisible();
    const intervalo = setInterval(cargarSiVisible, notificacionesAbiertas ? POLL_NOTIFICACIONES_ABIERTO_MS : POLL_NOTIFICACIONES_CERRADO_MS);
    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') cargarNotificacionesUsuario({ silenciosa: true });
    };
    document.addEventListener('visibilitychange', onVisibilityChange);
    return () => {
      clearInterval(intervalo);
      document.removeEventListener('visibilitychange', onVisibilityChange);
    };
  }, [cargarNotificacionesUsuario, enPanel, usuarioActual?.username, notificacionesAbiertas]);

  useEffect(() => {
    if (!(enPanel && usuarioActual?.username)) {
      setNotificacionesAbiertas(false);
      setNotificacionesUsuario([]);
      setNoLeidasUsuario(0);
      setWebPushSuscrito(false);
      notificacionesEnVueloRef.current = false;
      ultimaCargaNotificacionesRef.current = 0;
    }
  }, [enPanel, usuarioActual?.username]);

  const limpiarNotificaciones = useCallback(() => {
    setNotificacionesAbiertas(false);
    setNotificacionesUsuario([]);
    setNoLeidasUsuario(0);
  }, []);

  return {
    notificacionesUsuario,
    noLeidasUsuario,
    notificacionesAbiertas,
    cargandoNotificaciones,
    notificacionesNavegadorHabilitadas,
    abrirCentroNotificaciones,
    cerrarCentroNotificaciones,
    marcarNotificacionLeida,
    marcarTodasNotificacionesLeidas,
    toggleNotificacionesNavegador,
    limpiarNotificaciones
  };
}
