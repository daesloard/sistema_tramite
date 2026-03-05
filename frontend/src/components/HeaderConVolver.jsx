import { useEffect, useState } from 'react';

import { getHeaderConVolverStyles } from '../styles/components/HeaderConVolverStyles';
const styles = getHeaderConVolverStyles();

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