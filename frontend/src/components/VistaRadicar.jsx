import { Suspense, lazy } from 'react';
import HeaderConVolver from './HeaderConVolver';

const FormularioCertificado = lazy(() => import('./FormularioCertificado'));

const styles = {
  appRadicar: { minHeight: '100vh', background: '#f5f5f5' },
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

export default function VistaRadicar({ onVolver, onIrAVerificar }) {
  return (
    <div style={styles.appRadicar}>
      <HeaderConVolver onVolver={onVolver} titulo="Radicar Solicitud" />
      <Suspense fallback={<CargandoModulo texto="Cargando formulario de radicación..." />}>
        <FormularioCertificado onIrAVerificar={onIrAVerificar} />
      </Suspense>
    </div>
  );
}
