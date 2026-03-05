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
    padding: '14px 16px',
    borderRadius: theme.radius.lg,
    border: '1px solid transparent',
    boxShadow: theme.shadows.modal,
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
    background: '#1f2937',
    color: theme.colors.white,
    borderRadius: theme.radius.md,
    cursor: 'pointer',
    fontSize: '12px',
    fontWeight: 700,
    lineHeight: 1,
    padding: '8px 10px',
  },
});
