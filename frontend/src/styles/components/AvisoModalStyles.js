import { theme } from '../theme';

export const getAvisoModalStyles = () => ({
  overlay: {
    position: 'fixed',
    inset: 0,
    background: theme.overlay.modal,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '16px',
    zIndex: 120,
  },
  panel: {
    width: 'min(92vw, 520px)',
    padding: '16px 18px',
    borderRadius: theme.radius.xl,
    border: `1px solid ${theme.colors.borderNeutral}`,
    boxShadow: '0 24px 48px rgba(15,23,42,0.2)',
    background: 'linear-gradient(180deg, #ffffff 0%, #fbfdff 100%)',
  },
  text: {
    margin: 0,
    fontSize: '14px',
    fontWeight: 600,
    lineHeight: 1.45,
  },
  actions: {
    marginTop: '12px',
    display: 'flex',
    justifyContent: 'flex-end',
  },
  close: {
    border: 'none',
    background: 'linear-gradient(135deg, #0f172a 0%, #334155 100%)',
    color: theme.colors.white,
    borderRadius: '999px',
    cursor: 'pointer',
    fontSize: '12px',
    fontWeight: 700,
    lineHeight: 1,
    padding: '0.62rem 0.95rem',
    boxShadow: '0 12px 22px rgba(15,23,42,0.18)',
  },
});
