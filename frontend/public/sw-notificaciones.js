self.addEventListener('push', (event) => {
  let data = {
    title: 'Nueva notificacion',
    body: 'Tienes una nueva notificacion en el sistema',
    url: '/panel',
  };

  try {
    if (event.data) {
      const payload = event.data.json();
      data = {
        title: payload?.title || data.title,
        body: payload?.body || data.body,
        url: payload?.url || data.url,
      };
    }
  } catch {
    // keep fallback payload
  }

  const options = {
    body: data.body,
    icon: '/escudo.png',
    badge: '/escudo.png',
    data: { url: data.url },
    renotify: true,
  };

  event.waitUntil(self.registration.showNotification(data.title, options));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const destino = event.notification?.data?.url || '/panel';

  event.waitUntil((async () => {
    const allClients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    for (const client of allClients) {
      if ('focus' in client) {
        client.postMessage({ type: 'OPEN_NOTIFICATIONS_CENTER' });
        await client.focus();
        if ('navigate' in client) {
          await client.navigate(destino);
        }
        return;
      }
    }
    if (self.clients.openWindow) {
      await self.clients.openWindow(destino);
    }
  })());
});
