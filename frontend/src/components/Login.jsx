import { useState } from 'react';
import { API_AUTH_URL } from '../config/api';

import { getLoginStyles } from '../styles/components/LoginStyles';
const styles = getLoginStyles();

export default function Login({ onLoginSuccess, rol }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const usernameLimpio = username.trim();
      const passwordLimpia = password.trim();

      if (!usernameLimpio || !passwordLimpia) {
        throw new Error('Debes ingresar usuario y contraseña');
      }

      let response;
      for (let intento = 1; intento <= 2; intento += 1) {
        try {
          response = await fetch(`${API_AUTH_URL}/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: usernameLimpio, password: passwordLimpia })
          });
          break;
        } catch (fetchError) {
          if (intento === 2) {
            throw fetchError;
          }
          await new Promise((resolve) => setTimeout(resolve, 1500));
        }
      }

      if (!response.ok) {
        const contentType = response.headers.get('content-type') || '';
        let mensaje = 'Error de autenticación';

        if (contentType.includes('application/json')) {
          const errorJson = await response.json();
          mensaje = errorJson?.message || errorJson?.error || mensaje;
        } else {
          const errorText = await response.text();
          if (errorText) {
            mensaje = errorText;
          }
        }

        if (response.status >= 500) {
          mensaje = 'Error interno del servidor. Intenta nuevamente en unos segundos.';
        }

        throw new Error(mensaje);
      }

      const data = await response.json();

      // Verificar rol solo cuando se exige uno específico
      if (rol && data.rol !== rol) {
        throw new Error(`Acceso denegado. Se requiere rol: ${rol}`);
      }

      // Guardar token en localStorage
      localStorage.setItem('token', data.token);
      localStorage.setItem('user', JSON.stringify(data));

      // Llamar al callback
      onLoginSuccess(data);

    } catch (err) {
      const mensajeError = err?.message || 'Error de conexión';
      if (mensajeError.toLowerCase().includes('failed to fetch')) {
        setError('No se pudo conectar con el servidor. Si el backend está iniciando, espera unos segundos y vuelve a intentar.');
      } else {
        setError(mensajeError);
      }
    } finally {
      setLoading(false);
    }
  };

  const titulo = rol === 'VERIFICADOR' 
    ? '👤 Acceso Verificador' 
    : rol === 'ALCALDE' 
    ? '👨‍⚖️ Acceso Alcalde' 
    : rol === 'ADMINISTRADOR'
    ? '⚙️ Acceso Administrador'
    : '🔐 Inicio de Sesión';

  return (
    <div style={styles.loginContainer}>
      <div style={styles.loginCard}>
        <div style={styles.loginHeader}>
          <h2 style={styles.loginHeaderTitle}>{titulo}</h2>
          <p style={styles.loginHeaderSubtitle}>Ingresa tus credenciales para continuar</p>
        </div>

        {error && (
          <div style={styles.loginError}>
            <p style={styles.loginErrorText}>❌ {error}</p>
          </div>
        )}

        <form onSubmit={handleSubmit} style={styles.loginForm}>
          <div style={styles.formGroup}>
            <label htmlFor="username" style={styles.label}>Usuario</label>
            <input
              id="username"
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Ingresa tu usuario"
              required
              autoFocus
              style={styles.input}
            />
          </div>

          <div style={styles.formGroup}>
            <label htmlFor="password" style={styles.label}>Contraseña</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Ingresa tu contraseña"
              required
              style={styles.input}
            />
          </div>

          <button 
            type="submit" 
            disabled={loading}
            style={{
              ...styles.btnLogin,
              ...(loading ? styles.btnLoginDisabled : {}),
            }}
          >
            {loading ? '⏳ Iniciando...' : '🔐 Iniciar Sesión'}
          </button>
        </form>

        <div style={styles.loginFooter}>
          <p style={styles.footerText}>🔒 Acceso seguro con autenticación JWT</p>
        </div>
      </div>
    </div>
  );
}