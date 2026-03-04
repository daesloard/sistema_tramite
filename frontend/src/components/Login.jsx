import { useState } from 'react';
import { API_AUTH_URL } from '../config/api';

const styles = {
  loginContainer: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    padding: '20px',
  },
  loginCard: {
    background: 'white',
    borderRadius: '12px',
    boxShadow: '0 8px 24px rgba(0, 0, 0, 0.2)',
    padding: 'clamp(1rem, 5vw, 2.5rem)',
    maxWidth: '450px',
    width: '100%',
  },
  loginHeader: {
    textAlign: 'center',
    marginBottom: '30px',
  },
  loginHeaderTitle: {
    margin: '0 0 10px 0',
    color: '#2c3e50',
    fontSize: 'clamp(1.25rem, 5vw, 1.625rem)',
  },
  loginHeaderSubtitle: {
    margin: 0,
    color: '#7f8c8d',
    fontSize: '14px',
  },
  loginError: {
    background: '#ffe0e0',
    border: '1px solid #ff6b6b',
    color: '#c92a2a',
    padding: '12px',
    borderRadius: '6px',
    marginBottom: '20px',
    textAlign: 'center',
  },
  loginErrorText: {
    margin: 0,
    fontSize: '14px',
  },
  loginForm: {
    display: 'flex',
    flexDirection: 'column',
    gap: '20px',
  },
  formGroup: {
    display: 'flex',
    flexDirection: 'column',
    gap: '8px',
  },
  label: {
    fontSize: '14px',
    fontWeight: 600,
    color: '#2c3e50',
  },
  input: {
    padding: '12px 16px',
    border: '2px solid #ecf0f1',
    borderRadius: '6px',
    fontSize: '14px',
  },
  btnLogin: {
    padding: '10px 16px',
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    color: 'white',
    border: 'none',
    borderRadius: '6px',
    fontSize: '14px',
    fontWeight: 600,
    cursor: 'pointer',
    marginTop: '10px',
    width: 'auto',
    alignSelf: 'center',
    minWidth: '180px',
    whiteSpace: 'nowrap',
  },
  btnLoginDisabled: {
    background: '#bdc3c7',
    cursor: 'not-allowed',
    opacity: 0.6,
  },
  loginFooter: {
    marginTop: '30px',
    textAlign: 'center',
    paddingTop: '20px',
    borderTop: '1px solid #ecf0f1',
  },
  footerText: {
    margin: '8px 0',
    fontSize: '12px',
    color: '#7f8c8d',
  },
};

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

      const response = await fetch(`${API_AUTH_URL}/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: usernameLimpio, password: passwordLimpia })
      });

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
      setError(err.message);
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
