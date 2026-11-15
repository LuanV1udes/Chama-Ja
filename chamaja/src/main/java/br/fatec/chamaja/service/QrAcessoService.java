package br.fatec.chamaja.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QR code rotativo: prova de que o cliente está na entrada do estabelecimento.
 *
 * <p>O QR mostrado na tela de entrada carrega um código que muda a cada "janela" (1 minuto) e só
 * vale para a janela atual e a anterior. Quem tira uma foto e tenta usá-la depois já não consegue.
 * O código é uma assinatura (HMAC-SHA256) com um segredo do servidor, então não dá para inventar um.
 *
 * <p>Ao escanear um código válido o aparelho recebe uma <b>permissão</b> de uso único e curta
 * (10 minutos): dá tempo de digitar o nome, mas não permite pegar várias senhas em sequência.
 */
@Service
public class QrAcessoService {

    private static final Logger log = LoggerFactory.getLogger(QrAcessoService.class);
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

    private final byte[] segredo;
    private final long janelaSegundos;
    private final long permissaoSegundos;
    private final Clock relogio;

    /** Permissões já usadas (nonce → instante de expiração). Em memória: vale para uma instância só. */
    private final Map<String, Long> usadas = new ConcurrentHashMap<>();

    @Autowired
    public QrAcessoService(@Value("${chamaja.qr.segredo:}") String segredo,
                           @Value("${chamaja.qr.janela-segundos:60}") long janelaSegundos,
                           @Value("${chamaja.qr.permissao-minutos:10}") long permissaoMinutos) {
        this(segredo, janelaSegundos, permissaoMinutos * 60, Clock.systemUTC());
    }

    QrAcessoService(String segredo, long janelaSegundos, long permissaoSegundos, Clock relogio) {
        if (segredo == null || segredo.isBlank()) {
            byte[] aleatorio = new byte[32];
            new SecureRandom().nextBytes(aleatorio);
            this.segredo = aleatorio;
            log.info("QR_SEGREDO não definido: usando um segredo temporário. Os QR codes em exibição "
                    + "continuam valendo só por alguns minutos, então reiniciar o sistema não atrapalha.");
        } else {
            this.segredo = segredo.getBytes(StandardCharsets.UTF_8);
        }
        this.janelaSegundos = janelaSegundos;
        this.permissaoSegundos = permissaoSegundos;
        this.relogio = relogio;
    }

    // ------------------------------------------------------------------ código do QR

    /** Código que a tela de entrada coloca no QR agora. */
    public String tokenAtual(String codigoQr) {
        return token(codigoQr, janelaAtual());
    }

    /** O código é autêntico e foi gerado na janela atual ou na anterior? */
    public boolean tokenValido(String codigoQr, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        long janela = janelaAtual();
        // Sem "||": os dois testes sempre rodam, para o tempo de resposta não revelar nada.
        boolean atual = iguais(token(codigoQr, janela), token);
        boolean anterior = iguais(token(codigoQr, janela - 1), token);
        return atual | anterior;
    }

    public long getJanelaSegundos() {
        return janelaSegundos;
    }

    // ------------------------------------------------------------------ permissão do aparelho

    /** Valor do cookie de permissão (assinado, com validade e um número único). */
    public String emitirPermissao(String codigoQr) {
        long expira = agora() + permissaoSegundos;
        byte[] aleatorio = new byte[9];
        new SecureRandom().nextBytes(aleatorio);
        String nonce = BASE64.encodeToString(aleatorio);
        String corpo = expira + "." + nonce;
        return corpo + "." + assinar("p:" + codigoQr + ":" + corpo);
    }

    public long getPermissaoSegundos() {
        return permissaoSegundos;
    }

    /** A permissão é autêntica, não expirou e ainda não foi usada? (Não a consome.) */
    public boolean permissaoValida(String codigoQr, String valor) {
        return analisar(codigoQr, valor) != null && !usadas.containsKey(nonceDe(valor));
    }

    /** Usa a permissão: devolve true uma única vez; qualquer reuso depois devolve false. */
    public boolean consumirPermissao(String codigoQr, String valor) {
        String[] partes = analisar(codigoQr, valor);
        if (partes == null) {
            return false;
        }
        limparExpiradas();
        return usadas.putIfAbsent(partes[1], Long.parseLong(partes[0])) == null;
    }

    // ------------------------------------------------------------------ internos

    /** [expira, nonce] se a assinatura confere e a permissão não expirou; senão null. */
    private String[] analisar(String codigoQr, String valor) {
        if (valor == null) {
            return null;
        }
        String[] partes = valor.split("\\.");
        if (partes.length != 3) {
            return null;
        }
        long expira;
        try {
            expira = Long.parseLong(partes[0]);
        } catch (NumberFormatException e) {
            return null;
        }
        String esperada = assinar("p:" + codigoQr + ":" + partes[0] + "." + partes[1]);
        if (!iguais(esperada, partes[2]) || expira <= agora()) {
            return null;
        }
        return new String[]{partes[0], partes[1]};
    }

    private String nonceDe(String valor) {
        return valor.split("\\.")[1];
    }

    private void limparExpiradas() {
        long agora = agora();
        usadas.values().removeIf(expira -> expira <= agora);
    }

    private long agora() {
        return relogio.instant().getEpochSecond();
    }

    private long janelaAtual() {
        return agora() / janelaSegundos;
    }

    private String token(String codigoQr, long janela) {
        String assinatura = assinar("t:" + codigoQr + ":" + janela);
        return assinatura.substring(0, 22); // 128 bits bastam
    }

    private String assinar(String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo, "HmacSHA256"));
            return BASE64.encodeToString(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC indisponível", e);
        }
    }

    private static boolean iguais(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
