/*
 * Tela "Minha senha" (cliente).
 * - Busca o estado em /senha/{token}/estado.
 * - Atualiza na hora quando o servidor avisa por SSE e, por garantia, a cada 5 s (RNF03).
 * - RF05: avisa quando faltam até 3 números; RF06: avisa quando a senha é chamada.
 */
(function () {
  'use strict';

  var token = document.body.dataset.token;
  var porId = function (id) { return document.getElementById(id); };
  var ultimaChamada = null;
  var tituloOriginal = document.title;

  function mostrar(id, visivel) { porId(id).hidden = !visivel; }

  function tocarAviso() {
    try { if (navigator.vibrate) navigator.vibrate([300, 150, 300]); } catch (e) { /* sem vibração */ }
    try {
      var Contexto = window.AudioContext || window.webkitAudioContext;
      if (!Contexto) return;
      var ctx = new Contexto();
      var osc = ctx.createOscillator();
      osc.frequency.value = 880;
      osc.connect(ctx.destination);
      osc.start();
      setTimeout(function () { osc.stop(); ctx.close(); }, 250);
    } catch (e) { /* o navegador bloqueou o som: o aviso na tela continua valendo */ }
  }

  function mensagemFinal(status) {
    switch (status) {
      case 'FINALIZADA': return 'Atendimento finalizado. Obrigado!';
      case 'PULADA': return 'Sua senha foi pulada. Se ainda precisar de atendimento, pegue uma nova senha.';
      case 'CANCELADA': return 'Você saiu da fila.';
      default: return '';
    }
  }

  function desenharPromocoes(promocoes) {
    var lista = porId('lista-promocoes');
    lista.textContent = '';
    promocoes.forEach(function (p) {
      var item = document.createElement('li');

      var texto = document.createElement('div');
      texto.className = 'promo-txt';
      var titulo = document.createElement('strong');
      titulo.textContent = p.titulo;
      texto.appendChild(titulo);
      if (p.descricao) {
        var descricao = document.createElement('span');
        descricao.className = 'desc';
        descricao.textContent = p.descricao;
        texto.appendChild(descricao);
      }
      var validade = document.createElement('span');
      validade.className = 'validade';
      validade.textContent = 'válida até ' + p.validade;
      texto.appendChild(validade);
      item.appendChild(texto);

      var preco = document.createElement('span');
      preco.className = 'preco';
      preco.textContent = p.preco;
      item.appendChild(preco);

      lista.appendChild(item);
    });
    mostrar('promocoes', promocoes.length > 0);
  }

  function desenhar(e) {
    var aguardando = e.status === 'AGUARDANDO';
    var chamada = e.status === 'CHAMADA';
    var ativa = aguardando || chamada;

    porId('numero').textContent = e.numero;
    porId('atual').textContent = e.senhaAtual;
    porId('posicao').textContent = aguardando ? e.posicao + 'º' : '—';
    porId('afrente').textContent = aguardando ? e.aFrente : '—';

    mostrar('info', ativa);
    mostrar('aviso-chamada', chamada);
    mostrar('aviso-proximo', aguardando && e.proximo);
    mostrar('aviso-pausada', aguardando && e.filaEstado !== 'ABERTA');
    mostrar('form-sair', aguardando);
    mostrar('nova', !ativa);

    var final = mensagemFinal(e.status);
    var aviso = porId('aviso-final');
    aviso.textContent = final;
    aviso.hidden = final === '';

    desenharPromocoes(e.promocoes || []);

    // RF05: o servidor entrega "novoAviso" uma única vez por senha.
    if (e.novoAviso) tocarAviso();

    // RF06: toca na chamada e de novo a cada "repetir" (chamadaEm muda).
    if (chamada && e.chamadaEm !== ultimaChamada) tocarAviso();
    ultimaChamada = chamada ? e.chamadaEm : null;

    document.title = chamada ? '🔔 É a sua vez! · Chama Já'
      : (aguardando && e.proximo) ? '⏳ Sua vez está chegando · Chama Já'
      : tituloOriginal;
  }

  function atualizar() {
    fetch('/senha/' + encodeURIComponent(token) + '/estado', { cache: 'no-store' })
      .then(function (resposta) {
        if (resposta.status === 404) { window.location.reload(); return null; }
        return resposta.ok ? resposta.json() : null;
      })
      .then(function (estado) { if (estado) desenhar(estado); })
      .catch(function () { /* sem rede por um instante: tenta de novo no próximo ciclo */ });
  }

  atualizar();
  setInterval(atualizar, 5000);

  if (window.EventSource) {
    var fonte = new EventSource('/senha/' + encodeURIComponent(token) + '/eventos');
    fonte.addEventListener('fila', atualizar);
    fonte.addEventListener('pronto', atualizar);
  }
})();
