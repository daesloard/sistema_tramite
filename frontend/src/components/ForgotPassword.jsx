import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { API_AUTH_URL } from '../config/api';
import { getLoginStyles } from '../styles/components/LoginStyles';

const styles = getLoginStyles();

export default function ForgotPassword() {
  const [identificador, setIdentificador] = useState('');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setMessage('');
    setLoading(true);

    try {
      const id = identificador.trim();
      await fetch(`${API_AUTH_URL}/password/forgot`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ usuarioOEmail: id })
      });

      setMessage('Si existe una cuenta asociada, recibirás instrucciones en tu correo');
      // after success, redirect to login after short delay
      setTimeout(() => navigate('/login'), 2500);
    } catch (err) {
      setError('No se pudo enviar la solicitud. Intenta nuevamente.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.loginContainer}>
      <div style={styles.loginCard}>
        <div style={styles.loginHeader}>
          <h2 style={styles.loginHeaderTitle}>Recuperar contraseña</h2>
          <p style={styles.loginHeaderSubtitle}>Ingresa tu usuario o correo para recibir instrucciones</p>
        </div>

        {error && <div style={styles.loginError}><p style={styles.loginErrorText}>❌ {error}</p></div>}
        {message && <div style={{ marginBottom: 12, color: '#0b662f' }}>{message}</div>}

        <form onSubmit={handleSubmit} style={styles.loginForm}>
          <div style={styles.formGroup}>
            <label htmlFor="identificador" style={styles.label}>Usuario o correo</label>
            <input
              id="identificador"
              type="text"
              value={identificador}
              onChange={(e) => setIdentificador(e.target.value)}
              placeholder="Ingresa tu usuario o correo"
              required
              autoFocus
              style={styles.input}
            />
          </div>

          <button type="submit" disabled={loading} style={{ ...styles.btnLogin, ...(loading ? styles.btnLoginDisabled : {}) }}>
            {loading ? '⏳ Enviando...' : 'Enviar instrucciones'}
          </button>
        </form>

        <div style={styles.loginFooter}>
          <a href="/login" style={{ color: '#0b66ff', textDecoration: 'none' }}>Volver al inicio de sesión</a>
        </div>
      </div>
    </div>
  );
}
