import { useEffect, useState } from 'react';

const styles = {
  headerComun: {
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    color: '#fff',
    padding: 'clamp(0.9rem, 3.5vw, 1.5rem) clamp(0.9rem, 4vw, 2rem)',
    display: 'grid',
    gridTemplateColumns: 'auto 1fr auto',
    alignItems: 'center',
    gap: '0.9rem',
    position: 'sticky',
    top: 0,
    zIndex: 50,
  },
  headerCentro: { display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.7rem', minWidth: 0 },
  headerTitleCommon: { margin: 0, fontSize: 'clamp(1rem, 4.5vw, 1.5rem)', textAlign: 'center' },
  headerDerecha: { justifySelf: 'end' },
  headerEscudoPanel: {
    width: 'clamp(110px, 24vw, 250px)',
    height: 'auto',
    filter: 'drop-shadow(0 4px 10px rgba(0,0,0,0.25))',
  },
  btnVolver: {
    padding: '0.45rem 0.9rem',
    background: 'rgba(255,255,255,0.2)',
    color: '#fff',
    border: '2px solid #fff',
    borderRadius: '6px',
    cursor: 'pointer',
    fontWeight: 600,
    fontSize: '0.82rem',
    width: 'auto',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    whiteSpace: 'nowrap',
  },
};

export default function HeaderConVolver({ onVolver, titulo, derecha }) {
  const [esMovil, setEsMovil] = useState(() => {
    if (typeof window === 'undefined') return false;
    return window.innerWidth <= 768;
  });

  useEffect(() => {
    const handleResize = () => setEsMovil(window.innerWidth <= 768);
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  const headerStyle = esMovil
    ? { ...styles.headerComun, gridTemplateColumns: '1fr', justifyItems: 'center', rowGap: '0.65rem' }
    : styles.headerComun;

  const headerCentroStyle = esMovil
    ? { ...styles.headerCentro, flexDirection: 'column', gap: '0.4rem' }
    : styles.headerCentro;

  const headerDerechaStyle = esMovil
    ? { ...styles.headerDerecha, justifySelf: 'center' }
    : styles.headerDerecha;

  return (
    <header style={headerStyle}>
      <button style={styles.btnVolver} onClick={onVolver}>← Volver al Inicio</button>
      <div style={headerCentroStyle}>
        <img src="/escudo.png" alt="Escudo del municipio" style={styles.headerEscudoPanel} />
        {titulo ? <h1 style={styles.headerTitleCommon}>{titulo}</h1> : null}
      </div>
      <div style={headerDerechaStyle}>{derecha || null}</div>
    </header>
  );
}
