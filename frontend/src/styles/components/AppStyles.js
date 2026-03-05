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
    borderRadius: theme.radius.md,
    background: theme.colors.surface,
    boxShadow: theme.shadows.soft,
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
    borderRadius: theme.radius.sm,
    background: theme.colors.primary,
    color: theme.colors.white,
    fontWeight: 600,
    cursor: 'pointer',
    padding: '0.55rem 0.85rem',
  },
});