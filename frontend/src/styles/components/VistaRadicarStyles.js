import { theme } from '../theme';

export const getVistaRadicarStyles = () => ({
  appRadicar: { minHeight: '100vh', background: theme.colors.adminBg },
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
