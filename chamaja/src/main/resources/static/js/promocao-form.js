/*
 * Formulário de promoção: prévia do banner escolhido, aviso de arquivo grande e a opção de remover o atual.
 * (A conferência de verdade, do conteúdo e do tamanho, é feita no servidor; isto só poupa uma ida e volta.)
 */
(function () {
  'use strict';

  var LIMITE = 3 * 1024 * 1024;
  var entrada = document.getElementById('imagem');
  var previaNova = document.getElementById('previa-nova');
  var aviso = document.getElementById('aviso-imagem');
  var atual = document.getElementById('banner-atual');
  var remover = document.getElementById('remover');
  if (!entrada) return;

  var urlDaPrevia = null;

  function limparPrevia() {
    if (urlDaPrevia) { URL.revokeObjectURL(urlDaPrevia); urlDaPrevia = null; }
    previaNova.hidden = true;
    previaNova.removeAttribute('src');
  }

  entrada.addEventListener('change', function () {
    aviso.hidden = true;
    limparPrevia();
    var arquivo = entrada.files && entrada.files[0];
    if (!arquivo) return;

    if (arquivo.size > LIMITE) {
      aviso.textContent = 'A imagem é maior que 3 MB. Reduza o arquivo e tente de novo.';
      aviso.hidden = false;
      entrada.value = '';
      return;
    }
    if (arquivo.type && arquivo.type !== 'image/jpeg' && arquivo.type !== 'image/png') {
      aviso.textContent = 'Envie uma imagem JPG ou PNG.';
      aviso.hidden = false;
      entrada.value = '';
      return;
    }
    urlDaPrevia = URL.createObjectURL(arquivo);
    previaNova.src = urlDaPrevia;
    previaNova.hidden = false;
    // Um banner novo substitui o atual: a opção de remover deixa de fazer sentido.
    if (atual) atual.hidden = true;
    if (remover) remover.checked = false;
  });

  if (remover) {
    remover.addEventListener('change', function () {
      var imagemAtual = document.getElementById('previa-atual');
      if (imagemAtual) imagemAtual.style.opacity = remover.checked ? '.3' : '';
    });
  }
})();
