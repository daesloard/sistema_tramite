import { Suspense, lazy } from 'react';
import HeaderConVolver from './HeaderConVolver';

import { getVistaRadicarStyles } from '../styles/components/VistaRadicarStyles';
const FormularioCertificado = lazy(() => import('./FormularioCertificado'));

const styles = getVistaRadicarStyles();

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