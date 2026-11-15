/*
 * Service worker do Chama Já: recebe os avisos de Web Push mesmo com a página fechada e a tela do
 * celular bloqueada, e mostra a notificação. (Não guarda nada em cache: o sistema depende do servidor.)
 */
'use strict';

self.addEventListener('install', function () { self.skipWaiting(); });
self.addEventListener('activate', function (evento) { evento.waitUntil(self.clients.claim()); });

self.addEventListener('push', function (evento) {
  var dados = {};
  try {
    dados = evento.data ? evento.data.json() : {};
  } catch (erro) {
    dados = { titulo: 'Chama Já', corpo: evento.data ? evento.data.text() : '' };
  }

  evento.waitUntil((async function () {
    // Se o cliente está com a página aberta e visível, ela mesma já avisa (tela, vibração e som):
    // não duplica. (O navegador permite não mostrar notificação quando o site está visível.)
    // Exceção: a confirmação de "avisos ativados" (forcar) aparece sempre, para provar que funciona.
    if (!dados.forcar) {
      var janelas = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
      if (janelas.some(function (j) { return j.visibilityState === 'visible'; })) return;
    }

    await self.registration.showNotification(dados.titulo || 'Chama Já', {
      body: dados.corpo || '',
      icon: '/img/icone-192.png',
      badge: '/img/badge-96.png',
      // Mesmo "tag": a notificação nova substitui a anterior da mesma senha, sem empilhar.
      tag: 'chamaja-senha',
      renotify: true,
      vibrate: [300, 150, 300],
      requireInteraction: dados.tipo === 'chamada',
      data: { url: dados.url || '/' }
    });
  })());
});

self.addEventListener('notificationclick', function (evento) {
  evento.notification.close();
  var destino = (evento.notification.data && evento.notification.data.url) || '/';

  evento.waitUntil((async function () {
    var janelas = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    for (var i = 0; i < janelas.length; i++) {
      if (janelas[i].url.indexOf(destino) !== -1 && 'focus' in janelas[i]) {
        return janelas[i].focus();
      }
    }
    return self.clients.openWindow(destino);
  })());
});
