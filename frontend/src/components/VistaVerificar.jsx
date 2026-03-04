import { Suspense, lazy } from 'react';
import HeaderConVolver from './HeaderConVolver';

const VerificadorCertificado = lazy(() => import('./VerificadorCertificado'));

const styles = {
  appVerificar: { minHeight: '100vh', background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' },
  moduloLoading: {
    minHeight: '40vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: '#4b5563',
    fontSize: '1rem',
    fontWeight: 600,
    padding: '1rem',
  },
};

function CargandoModulo({ texto = 'Cargando módulo...' }) {
  return <div style={styles.moduloLoading}>{texto}</div>;
}

export default function VistaVerificar({ onVolver }) {
  return (
    <div style={styles.appVerificar}>
      <HeaderConVolver onVolver={onVolver} />
      <Suspense fallback={<CargandoModulo texto="Cargando verificador..." />}>
        <VerificadorCertificado />
      </Suspense>
    </div>
  );
}
