import { theme } from '../theme';

export const getLoginStyles = () => ({
  loginContainer: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: theme.gradients.alcaldeBackground,
    padding: '20px',
  },
  loginCard: {
    background: theme.colors.white,
    borderRadius: theme.radius.xl,
    boxShadow: '0 8px 24px rgba(0, 0, 0, 0.2)',
    padding: 'clamp(1rem, 5vw, 2.5rem)',
    maxWidth: '450px',
    width: '100%',
  },
  loginHeader: {
    textAlign: 'center',
    marginBottom: '30px',
  },
  loginHeaderTitle: {
    margin: '0 0 10px 0',
    color: theme.colors.textPrimary,
    fontSize: 'clamp(1.25rem, 5vw, 1.625rem)',
  },
  loginHeaderSubtitle: {
    margin: 0,
    color: theme.colors.textSecondary,
    fontSize: '14px',
  },
  loginError: {
    background: theme.colors.dangerBg,
    border: `1px solid ${theme.colors.dangerBorder}`,
    color: theme.colors.dangerText,
    padding: '12px',
    borderRadius: theme.radius.md,
    marginBottom: '20px',
    textAlign: 'center',
  },
  loginErrorText: {
    margin: 0,
    fontSize: '14px',
  },
  loginForm: {
    display: 'flex',
    flexDirection: 'column',
    gap: '20px',
  },
  formGroup: {
    display: 'flex',
    flexDirection: 'column',
    gap: '8px',
  },
  label: {
    fontSize: '14px',
    fontWeight: 600,
    color: theme.colors.textPrimary,
  },
  input: {
    padding: '12px 16px',
    border: '2px solid #ecf0f1',
    borderRadius: theme.radius.md,
    fontSize: '14px',
  },
  btnLogin: {
    padding: '10px 16px',
    background: theme.gradients.alcaldeBackground,
    color: theme.colors.white,
    border: 'none',
    borderRadius: theme.radius.md,
    fontSize: '14px',
    fontWeight: 600,
    cursor: 'pointer',
    marginTop: '10px',
    width: 'auto',
    alignSelf: 'center',
    minWidth: '180px',
    whiteSpace: 'nowrap',
  },
  btnLoginDisabled: {
    background: '#bdc3c7',
    cursor: 'not-allowed',
    opacity: 0.6,
  },
  loginFooter: {
    marginTop: '30px',
    textAlign: 'center',
    paddingTop: '20px',
    borderTop: '1px solid #ecf0f1',
  },
  footerText: {
    margin: '8px 0',
    fontSize: '12px',
    color: theme.colors.textSecondary,
  },
});
