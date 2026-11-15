package br.fatec.chamaja.service;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Urgency;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.concurrent.TimeUnit;

/**
 * Envio real de Web Push: criptografa a mensagem para o navegador (RFC 8291) e se identifica ao serviço
 * de push com as chaves VAPID do servidor (RFC 8292).
 */
@Component
public class WebPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);
    private static final int VALIDADE_URGENTE_S = 120;
    private static final int VALIDADE_NORMAL_S = 600;

    private final String chavePublica;
    private final String chavePrivada;
    private final String assunto;
    private PushService servico;

    public WebPushSender(@Value("${chamaja.push.publica:}") String chavePublica,
                         @Value("${chamaja.push.privada:}") String chavePrivada,
                         @Value("${chamaja.push.assunto:https://localhost}") String assunto) {
        this.chavePublica = chavePublica;
        this.chavePrivada = chavePrivada;
        this.assunto = assunto;
    }

    /**
     * A biblioteca pede o provedor "BC" pelo nome, inclusive ao ler a chave do navegador (antes de existir
     * o serviço). Por isso ele é registrado no começo de todo envio, e não só ao criar o serviço.
     */
    private static void garantirProvedor() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /** Criado só no primeiro envio: sem chaves (ou nos testes) a aplicação sobe sem tocar na biblioteca. */
    private synchronized PushService servico() throws GeneralSecurityException {
        if (servico == null) {
            servico = new PushService(chavePublica, chavePrivada, assunto);
        }
        return servico;
    }

    @Override
    public Resultado enviar(String endpoint, String p256dh, String auth, String payload, boolean urgente) {
        try {
            garantirProvedor();
            Notification notificacao = Notification.builder()
                    .endpoint(endpoint)
                    .userPublicKey(p256dh)
                    .userAuth(auth)
                    .payload(payload)
                    .ttl(urgente ? VALIDADE_URGENTE_S : VALIDADE_NORMAL_S)
                    .urgency(urgente ? Urgency.HIGH : Urgency.NORMAL)
                    .build();
            HttpResponse resposta = servico()
                    .sendAsync(notificacao, Encoding.AES128GCM)
                    .get(10, TimeUnit.SECONDS);
            int status = resposta.getStatusLine().getStatusCode();
            if (status >= 200 && status < 300) {
                return Resultado.ENVIADA;
            }
            if (status == 404 || status == 410) {
                return Resultado.EXPIRADA;
            }
            log.warn("Serviço de push {} recusou o aviso: HTTP {}", host(endpoint), status);
            return Resultado.ERRO;
        } catch (Exception e) {
            // Não registra o endpoint inteiro: ele identifica o aparelho do cliente.
            log.warn("Falha ao enviar push para {}: {}", host(endpoint), e.toString());
            return Resultado.ERRO;
        }
    }

    private static String host(String endpoint) {
        try {
            return URI.create(endpoint).getHost();
        } catch (RuntimeException e) {
            return "?";
        }
    }
}
