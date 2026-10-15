// Namastute's service worker (Learning Notes 58.2): the only code a push can wake when no tab is open.
// Deliberately tiny: no caching, no offline mode.

self.addEventListener("push", (event) => {
  const { title, body } = event.data ? event.data.json() : { title: "Namastute", body: "" };
  // tag: a second "New message" replaces the first instead of stacking up
  event.waitUntil(self.registration.showNotification(title, { body, icon: "/icon-192.png", tag: title }));
});

// Tapping the notification brings back an open Namastute tab, or opens one
self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  event.waitUntil(
    self.clients
      .matchAll({ type: "window", includeUncontrolled: true })
      .then((tabs) => (tabs.length ? tabs[0].focus() : self.clients.openWindow("/"))),
  );
});
