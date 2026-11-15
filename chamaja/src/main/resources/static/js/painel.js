/*
 * Painel do operador: recarrega a página quando a fila muda por fora
 * (por exemplo, um cliente sai da fila ou retira uma senha nova).
 */
(function () {
  'use strict';

  if (!window.EventSource) return;

  var fonte = new EventSource(document.body.dataset.eventos);
  var pronto = false;
  var agendado = null;

  // O primeiro evento ("pronto") só confirma a conexão; os seguintes indicam mudança.
  fonte.addEventListener('pronto', function () { pronto = true; });
  fonte.addEventListener('fila', function () {
    if (!pronto || agendado) return;
    agendado = setTimeout(function () { window.location.reload(); }, 400);
  });
})();
