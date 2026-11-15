/*
 * Tela de entrada (tablet da porta): troca o QR code a cada poucos segundos, antes de o código
 * anterior vencer, e mantém a tela do tablet acordada.
 */
(function () {
  'use strict';

  var img = document.getElementById('qr');
  var erro = document.getElementById('erro');
  var base = img.getAttribute('src').split('?')[0];
  var segundos = Math.max(5, parseInt(document.body.dataset.intervalo, 10) || 20);

  function atualizar() {
    var novo = new Image();
    novo.onload = function () { img.src = novo.src; erro.hidden = true; };
    novo.onerror = function () { erro.hidden = false; };
    novo.src = base + '?_=' + Date.now();   // o parâmetro só impede o cache do navegador
  }

  setInterval(atualizar, segundos * 1000);

  // Evita que o tablet apague a tela (nem todo navegador oferece; sem ele, só a configuração do aparelho).
  var trava = null;
  function manterAcordado() {
    if (!('wakeLock' in navigator)) return;
    navigator.wakeLock.request('screen').then(function (t) { trava = t; }).catch(function () { /* recusado */ });
  }
  manterAcordado();
  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState === 'visible') { atualizar(); manterAcordado(); }
  });
})();
