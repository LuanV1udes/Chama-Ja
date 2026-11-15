package br.fatec.chamaja.web;

import br.fatec.chamaja.model.Estabelecimento;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Monta os endereços públicos do sistema (usa APP_BASE_URL, ou o endereço da requisição). */
@Component
public class UrlsPublicas {

    private final String baseUrl;

    public UrlsPublicas(@Value("${chamaja.base-url:}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    private String base() {
        return baseUrl.isBlank()
                ? ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                : baseUrl.replaceAll("/+$", "");
    }

    /** O que vai dentro do QR code: a página do cliente com o código do momento. */
    public String urlDoCliente(Estabelecimento est, String token) {
        return base() + "/q/" + est.getCodigoQr() + "?t=" + token;
    }

    /** Link secreto da tela de entrada (o que o tablet da entrada abre). */
    public String urlDaTela(Estabelecimento est) {
        return base() + "/tela/" + est.getCodigoTela();
    }
}
