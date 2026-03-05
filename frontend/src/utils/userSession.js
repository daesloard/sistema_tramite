export const getStoredUser = () => {
  try {
    const raw = localStorage.getItem('user');
    if (!raw) return null;
    return JSON.parse(raw);
  } catch {
    return null;
  }
};

export const getStoredUsername = () => {
  const user = getStoredUser();
  const username = user?.username;
  return typeof username === 'string' ? username.trim() : '';
};

export const buildUsernameHeader = (headerName = 'X-Username') => {
  const username = getStoredUsername();
  if (!username) return {};
  return { [headerName]: username };
};
