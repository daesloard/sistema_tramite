import { theme } from '../theme';

export const getHeaderConVolverStyles = () => ({
  headerComun: {
    background: theme.gradients.alcaldeBackground,
    color: theme.colors.white,
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
    color: theme.colors.white,
    border: `2px solid ${theme.colors.white}`,
    borderRadius: theme.radius.md,
    cursor: 'pointer',
    fontWeight: 600,
    fontSize: '0.82rem',
    width: 'auto',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    whiteSpace: 'nowrap',
  },
});
