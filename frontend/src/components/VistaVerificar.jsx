import { Suspense, lazy } from 'react';
import HeaderConVolver from './HeaderConVolver';

import { getVistaVerificarStyles } from '../styles/components/VistaVerificarStyles';
const VerificadorCertificado = lazy(() => import('./VerificadorCertificado'));

const styles = getVistaVerificarStyles();

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