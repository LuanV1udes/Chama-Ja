/*
 * Avisos no celular (Web Push), tela "Minha senha".
 *
 * Fluxo: assim que o cliente chega aqui (depois de escanear o QR e informar o nome), aparece uma
 * mensagem animada convidando a permitir notificações. Se ele aceitar, o navegador se inscreve no
 * serviço de push e a inscrição é ligada à senha dele; a partir daí o servidor avisa quando faltarem
 * poucos números e quando a senha for chamada, mesmo com a tela bloqueada.
 *
 * Casos: já permitiu antes (só reinscreve, sem perguntar), bloqueou (não insiste), iPhone (só funciona
 * com o site na Tela de Início: mostra a instrução) e navegador sem suporte (não faz nada).
 */
(function () {
  'use strict';

  var corpo = document.body;
  var chave = corpo.dataset.pushChave;       // ausente = o servidor não tem Web Push configurado
  var token = corpo.dataset.token;
  var card = document.getElementById('push-card');
  if (!chave || !card) return;

  var ativoMsg = document.getElementById('push-ativo');
  var botaoPermitir = document.getElementById('push-permitir');
  var DISPENSA = 'chamaja_push_dispensado';  // dia em que o cliente respondeu "agora não"
  var iniciado = false;

  var suportado = 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
  var ehIos = /iphone|ipad|ipod/i.test(navigator.userAgent) ||
    (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  var instalado = window.navigator.standalone === true ||
    (window.matchMedia && window.matchMedia('(display-mode: standalone)').matches);

  // ---------------------------------------------------------------- utilidades

  function hoje() { return new Date().toISOString().slice(0, 10); }
  function dispensadoHoje() {
    try { return localStorage.getItem(DISPENSA) === hoje(); } catch (e) { return false; }
  }
  function dispensar() {
    try { localStorage.setItem(DISPENSA, hoje()); } catch (e) { /* sem armazenamento: pergunta de novo */ }
  }
  function csrf() {
    var campo = document.querySelector('input[name="_csrf"]');
    return campo ? campo.value : '';
  }
  function base64ParaBytes(texto) {
    var base = (texto + '='.repeat((4 - texto.length % 4) % 4)).replace(/-/g, '+').replace(/_/g, '/');
    var bruto = atob(base);
    var bytes = new Uint8Array(bruto.length);
    for (var i = 0; i < bruto.length; i++) bytes[i] = bruto.charCodeAt(i);
    return bytes;
  }
  function mesmaChave(inscricao, esperada) {
    var opcoes = inscricao.options && inscricao.options.applicationServerKey;
    if (!opcoes) return true;
    var atual = new Uint8Array(opcoes);
    if (atual.length !== esperada.length) return false;
    for (var i = 0; i < atual.length; i++) if (atual[i] !== esperada[i]) return false;
    return true;
  }

  // ---------------------------------------------------------------- mensagem animada

  /** Estados da mensagem: pedir, ok, erro, negado, ios. */
  function mostrar(estado) {
    var secoes = card.querySelectorAll('[data-estado]');
    for (var i = 0; i < secoes.length; i++) secoes[i].hidden = secoes[i].dataset.estado !== estado;
    card.dataset.estadoAtual = estado;
    if (card.hidden) {
      card.hidden = false;
      corpo.classList.add('com-push-card');
    }
    // Sino e confirmação reiniciam a animação sempre que o estado muda.
    card.classList.remove('animar');
    void card.offsetWidth;
    card.classList.add('animar');
  }

  function esconder() {
    if (card.hidden) return;
    card.classList.add('saindo');
    setTimeout(function () {
      card.hidden = true;
      card.classList.remove('saindo');
      corpo.classList.remove('com-push-card');
    }, 350);
  }

  function marcarAtivo() {
    if (ativoMsg) ativoMsg.hidden = false;
  }

  // ---------------------------------------------------------------- inscrição

  function inscrever() {
    var esperada = base64ParaBytes(chave);
    return navigator.serviceWorker.register('/sw.js')
      .then(function () { return navigator.serviceWorker.ready; })
      .then(function (registro) {
        return registro.pushManager.getSubscription().then(function (existente) {
          // Chaves do servidor trocadas: a inscrição antiga não serve mais.
          if (existente && !mesmaChave(existente, esperada)) {
            return existente.unsubscribe().then(function () { return null; });
          }
          return existente;
        }).then(function (existente) {
          return existente || registro.pushManager.subscribe({
            userVisibleOnly: true,
            applicationServerKey: esperada
          });
        });
      })
      .then(function (inscricao) {
        return fetch('/senha/' + encodeURIComponent(token) + '/push', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf() },
          body: JSON.stringify(inscricao.toJSON())
        });
      })
      .then(function (resposta) {
        if (!resposta.ok) throw new Error('HTTP ' + resposta.status);
      });
  }

  function pedirPermissao() {
    return new Promise(function (resolve) {
      var r = Notification.requestPermission(resolve);   // Safari antigo usa callback
      if (r && typeof r.then === 'function') r.then(resolve);
    });
  }

  function aoPermitir() {
    botaoPermitir.disabled = true;
    pedirPermissao().then(function (permissao) {
      if (permissao === 'granted') {
        return inscrever().then(function () {
          mostrar('ok');
          marcarAtivo();
          setTimeout(esconder, 3200);
        });
      }
      botaoPermitir.disabled = false;
      mostrar(permissao === 'denied' ? 'negado' : 'pedir');
    }).catch(function () {
      botaoPermitir.disabled = false;
      mostrar('erro');
    });
  }

  // ---------------------------------------------------------------- decisão

  function decidir() {
    if (!suportado) {
      // iPhone no Safari comum: sem Web Push. Só a instalação na Tela de Início o libera.
      if (ehIos && !instalado && !dispensadoHoje()) setTimeout(function () { mostrar('ios'); }, 700);
      return;
    }
    navigator.serviceWorker.register('/sw.js').catch(function () { /* tenta de novo ao permitir */ });

    var permissao = Notification.permission;
    if (permissao === 'granted') {
      // Já permitiu antes (outra senha, outro dia): liga este aparelho à senha nova sem perguntar.
      inscrever().then(marcarAtivo).catch(function () { /* continua com o aviso na tela */ });
    } else if (permissao === 'default' && !dispensadoHoje()) {
      setTimeout(function () { mostrar('pedir'); }, 700);
    }
    // 'denied': respeita a escolha e não insiste.
  }

  botaoPermitir.addEventListener('click', aoPermitir);
  document.getElementById('push-depois').addEventListener('click', function () {
    dispensar();
    esconder();
  });
  var entendi = card.querySelectorAll('[data-fechar]');
  for (var i = 0; i < entendi.length; i++) {
    entendi[i].addEventListener('click', function () {
      if (card.dataset.estadoAtual === 'ios') dispensar();
      esconder();
    });
  }

  // A tela da senha avisa o estado (aguardando, chamada, finalizada...). Só oferece enquanto a senha vale.
  document.addEventListener('chamaja:estado', function (evento) {
    var estado = evento.detail && evento.detail.status;
    var ativa = estado === 'AGUARDANDO' || estado === 'CHAMADA';
    if (!ativa) {
      esconder();
      if (ativoMsg) ativoMsg.hidden = true;
      return;
    }
    if (!iniciado) {
      iniciado = true;
      decidir();
    }
  });
})();
