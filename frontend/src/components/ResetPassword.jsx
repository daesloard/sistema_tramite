import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { API_AUTH_URL } from '../config/api';
import { getLoginStyles } from '../styles/components/LoginStyles';

const styles = getLoginStyles();
const POLICY = /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/;

export default function ResetPassword() {
  const navigate = useNavigate();
  const location = useLocation();
  const params = new URLSearchParams(location.search);
  const tokenFromQuery = params.get('token') || '';

  const [token, setToken] = useState(tokenFromQuery);
  const [nueva, setNueva] = useState('');
  const [confirmar, setConfirmar] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (tokenFromQuery) setToken(tokenFromQuery);
  }, [tokenFromQuery]);

  const validarCliente = () => {
    if (!token || token.trim() === '') {
      setError('El token es requerido');
      return false;
    }
    if (!POLICY.test(nueva)) {
      setError('La contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula, un número y un carácter especial');
      return false;
    }
    if (nueva !== confirmar) {
      setError('La confirmación de contraseña no coincide');
      return false;
    }
    return true;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setMessage('');

    if (!validarCliente()) return;

    setLoading(true);
    try {
      const res = await fetch(`${API_AUTH_URL}/password/reset`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token: token.trim(), nuevaContrasena: nueva, confirmarContrasena: confirmar })
      });

      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || 'Error al restablecer contraseña');
      }

      setMessage('Contraseña actualizada correctamente. Redirigiendo al inicio de sesión...');
      setTimeout(() => navigate('/login'), 2000);
    } catch (err) {
      const msg = err?.message || 'Error de conexión';
      setError(msg.replace(/^\s+|\s+$/g, ''));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.loginContainer}>
      <div style={styles.loginCard}>
        <div style={styles.loginHeader}>
          <h2 style={styles.loginHeaderTitle}>Restablecer contraseña</h2>
          <p style={styles.loginHeaderSubtitle}>Ingresa el token y la nueva contraseña</p>
        </div>

        {error && <div style={styles.loginError}><p style={styles.loginErrorText}>❌ {error}</p></div>}
        {message && <div style={{ marginBottom: 12, color: '#0b662f' }}>{message}</div>}

        <form onSubmit={handleSubmit} style={styles.loginForm}>
          <div style={styles.formGroup}>
            <label htmlFor="token" style={styles.label}>Token</label>
            <input id="token" type="text" value={token} onChange={(e) => setToken(e.target.value)} placeholder="Token recibido por correo" style={styles.input} required />
          </div>

          <div style={styles.formGroup}>
            <label htmlFor="nueva" style={styles.label}>Nueva contraseña</label>
            <input id="nueva" type="password" value={nueva} onChange={(e) => setNueva(e.target.value)} placeholder="Nueva contraseña" style={styles.input} required />
          </div>

          <div style={styles.formGroup}>
            <label htmlFor="confirmar" style={styles.label}>Confirmar contraseña</label>
            <input id="confirmar" type="password" value={confirmar} onChange={(e) => setConfirmar(e.target.value)} placeholder="Confirmar contraseña" style={styles.input} required />
          </div>

          <button type="submit" disabled={loading} style={{ ...styles.btnLogin, ...(loading ? styles.btnLoginDisabled : {}) }}>
            {loading ? '⏳ Restableciendo...' : 'Restablecer contraseña'}
          </button>
        </form>

        <div style={styles.loginFooter}>
          <a href="/login" style={{ color: '#0b66ff', textDecoration: 'none' }}>Volver al inicio de sesión</a>
        </div>
      </div>
    </div>
  );
}
