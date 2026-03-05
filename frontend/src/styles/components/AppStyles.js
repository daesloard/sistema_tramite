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
});