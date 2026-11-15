package br.fatec.chamaja.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Confere os dados que o navegador do cliente manda ao se inscrever no Web Push.
 *
 * <p><b>Por que isso importa (SSRF):</b> o servidor faz requisições HTTPS para o "endpoint" que o
 * cliente informa. Sem conferir, alguém poderia cadastrar um endereço interno (a rede da Railway,
 * serviços de metadados, localhost) e usar o servidor para atacá-los. Por isso só aceitamos endereços
 * HTTPS dos serviços de push dos navegadores.
 */
public final class EnderecoPush {

    /** Serviços de push dos navegadores: Chrome/Android, Firefox, Safari/iOS e Edge/Windows. */
    private static final List<String> DOMINIOS = List.of(
            "fcm.googleapis.com",
            "android.googleapis.com",
            "push.services.mozilla.com",
            "push.apple.com",
            "notify.windows.com");

    private EnderecoPush() {
    }

    public static boolean valido(String endpoint) {
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > 1000) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null) {
            return false;
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        String minusculo = host.toLowerCase(Locale.ROOT);
        // "equals" ou subdomínio de verdade ("." + domínio): "fcm.googleapis.com.evil.com" não passa.
        return DOMINIOS.stream().anyMatch(d -> minusculo.equals(d) || minusculo.endsWith("." + d));
    }

    /** p256dh é um ponto P-256 não comprimido (65 bytes, começa com 0x04); auth tem 16 bytes. */
    public static boolean chavesValidas(String p256dh, String auth) {
        try {
            byte[] chave = Base64.getUrlDecoder().decode(p256dh);
            byte[] segredo = Base64.getUrlDecoder().decode(auth);
            return chave.length == 65 && chave[0] == 0x04 && segredo.length == 16;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }
}
