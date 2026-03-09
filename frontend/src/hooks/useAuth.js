import { useState, useCallback } from 'react';

const obtenerUsuarioGuardado = () => {
  const raw = localStorage.getItem('usuario') || localStorage.getItem('user');
  if (!raw) return null;

  try {
    const user = JSON.parse(raw);
    return user?.rol ? user : null;
  } catch {
    return null;
  }
};

export function useAuth(navigate, location, rutaPanelPorRol, esRutaInterna) {
  const [sesionHidratada] = useState(true);
  const [usuarioActual, setUsuarioActual] = useState(() => obtenerUsuarioGuardado());

  const limpiarSesionLocal = useCallback(() => {
    localStorage.removeItem('token');
    localStorage.removeItem('usuario');
    localStorage.removeItem('user');
  }, []);

  const cerrarSesion = useCallback((onSesionCerrada) => {
    setUsuarioActual(null);
    limpiarSesionLocal();
    if (onSesionCerrada) onSesionCerrada();
    navigate('/', { replace: true });
  }, [limpiarSesionLocal, navigate]);

  const cambiarUsuario = useCallback((onSesionCerrada) => {
    const from = location.pathname.startsWith('/panel') ? location.pathname : '/panel';
    setUsuarioActual(null);
    limpiarSesionLocal();
    if (onSesionCerrada) onSesionCerrada();
    navigate('/login', { state: { from }, replace: true });
  }, [limpiarSesionLocal, location.pathname, navigate]);

  const handleLoginUnificado = useCallback((usuario) => {
    const from = location.state?.from;
    const rutaRol = rutaPanelPorRol(usuario?.rol) || '/panel';
    const destino = esRutaInterna(from) ? from : rutaRol;

    setUsuarioActual(usuario);
    localStorage.setItem('usuario', JSON.stringify(usuario));
    localStorage.setItem('user', JSON.stringify(usuario));
    navigate(destino, { replace: true });
  }, [location.state, navigate, rutaPanelPorRol, esRutaInterna]);

  return {
    sesionHidratada,
    usuarioActual,
    cerrarSesion,
    cambiarUsuario,
    handleLoginUnificado,
  };
}
