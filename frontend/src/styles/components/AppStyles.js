import { theme } from '../theme';

export const getAppStyles = () => ({
  moduloLoading: {
    minHeight: '40vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: theme.colors.textLight,
    fontSize: '1rem',
    fontWeight: 600,
    padding: '1rem',
  },
  moduloErrorWrap: {
    minHeight: '50vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '1rem',
  },
  moduloErrorCard: {
    width: '100%',
    maxWidth: '680px',
    border: `1px solid ${theme.colors.borderSoft}`,
    borderRadius: theme.radius.xl,
    background: 'linear-gradient(180deg, #ffffff 0%, #fbfdff 100%)',
    boxShadow: theme.shadows.cardStrong,
    padding: '1rem 1.25rem',
  },
  moduloErrorTitle: {
    margin: '0 0 0.5rem 0',
    color: theme.colors.textStrong,
    fontSize: '1.1rem',
  },
  moduloErrorText: {
    margin: '0 0 0.75rem 0',
    color: theme.colors.textMuted,
    lineHeight: 1.45,
  },
  moduloErrorButtonRow: {
    display: 'flex',
    gap: '0.5rem',
    flexWrap: 'wrap',
  },
  moduloErrorButton: {
    border: 'none',
    borderRadius: '999px',
    background: 'linear-gradient(135deg, #1d4ed8 0%, #2563eb 60%, #38bdf8 100%)',
    color: theme.colors.white,
    fontWeight: 700,
    cursor: 'pointer',
    padding: '0.7rem 1rem',
    boxShadow: '0 12px 22px rgba(37,99,235,0.18)',
  },
});