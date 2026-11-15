package br.fatec.chamaja;

import br.fatec.chamaja.service.PushSender.Resultado;
import br.fatec.chamaja.service.WebPushSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Envio REAL de Web Push (a biblioteca de verdade), contra um servidor local que faz o papel do serviço de push.
 * Confere o que o Chrome, o Firefox e o Safari exigem: cabeçalho VAPID com assinatura ES256 válida,
 * mensagem criptografada (aes128gcm) e validade/urgência.
 */
class WebPushSenderTest {

    private static final String ASSUNTO = "https://chama-ja.exemplo.com.br";
    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer servidor;
    private final AtomicInteger statusDaResposta = new AtomicInteger(201);
    private final AtomicReference<com.sun.net.httpserver.Headers> cabecalhos = new AtomicReference<>();
    private final AtomicReference<byte[]> corpo = new AtomicReference<>();

    private KeyPair servidorVapid;      // chaves VAPID do Chama Já
    private KeyPair navegador;          // chaves do navegador do cliente
    private String authDoNavegador;
    private WebPushSender sender;
    private String endpoint;

    @BeforeEach
    void subirServidor() throws Exception {
        servidorVapid = novoPar();
        navegador = novoPar();
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);
        authDoNavegador = b64(auth);

        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/push", troca -> {
            cabecalhos.set(troca.getRequestHeaders());
            ByteArrayOutputStream lido = new ByteArrayOutputStream();
            troca.getRequestBody().transferTo(lido);
            corpo.set(lido.toByteArray());
            troca.sendResponseHeaders(statusDaResposta.get(), -1);
            troca.close();
        });
        servidor.start();
        endpoint = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/push/abc";

        sender = new WebPushSender(publica(servidorVapid), privada(servidorVapid), ASSUNTO);
    }

    @AfterEach
    void pararServidor() {
        servidor.stop(0);
    }

    // ------------------------------------------------------------------ o que o serviço de push recebe

    @Test
    void enviaComCabecalhosEMensagemNoFormatoDoWebPush() {
        Resultado resultado = sender.enviar(endpoint, publica(navegador), authDoNavegador,
                "{\"titulo\":\"É a sua vez!\"}", true);

        assertThat(resultado).isEqualTo(Resultado.ENVIADA);
        var h = cabecalhos.get();
        assertThat(h.getFirst("Content-Encoding")).isEqualTo("aes128gcm");
        assertThat(h.getFirst("TTL")).isEqualTo("120");           // urgente: validade curta
        assertThat(h.getFirst("Urgency")).isEqualToIgnoringCase("high");
        // Cabeçalho do formato aes128gcm: 16 (salt) + 4 (tamanho) + 1 + 65 (chave do servidor) = 86 bytes
        // antes do texto criptografado (que carrega ainda 16 bytes de autenticação).
        assertThat(corpo.get().length).isGreaterThan(86 + 16);
    }

    @Test
    void avisoNormalTemValidadeMaisLongaEUrgenciaNormal() {
        sender.enviar(endpoint, publica(navegador), authDoNavegador, "{\"titulo\":\"x\"}", false);

        assertThat(cabecalhos.get().getFirst("TTL")).isEqualTo("600");
        assertThat(cabecalhos.get().getFirst("Urgency")).isEqualToIgnoringCase("normal");
    }

    @Test
    void oCabecalhoVapidIdentificaOServidorComAssinaturaValida() throws Exception {
        sender.enviar(endpoint, publica(navegador), authDoNavegador, "{\"titulo\":\"x\"}", true);

        String autorizacao = cabecalhos.get().getFirst("Authorization");
        assertThat(autorizacao).startsWith("vapid t=").contains(", k=");
        String jwt = autorizacao.substring("vapid t=".length(), autorizacao.indexOf(", k="));
        String chavePublicaNoCabecalho = autorizacao.substring(autorizacao.indexOf(", k=") + 4);
        assertThat(chavePublicaNoCabecalho).isEqualTo(publica(servidorVapid));

        String[] partes = jwt.split("\\.");
        JsonNode cabecalho = JSON.readTree(Base64.getUrlDecoder().decode(partes[0]));
        JsonNode carga = JSON.readTree(Base64.getUrlDecoder().decode(partes[1]));
        assertThat(cabecalho.get("alg").asText()).isEqualTo("ES256");
        // "aud" é a origem do serviço de push; "sub" identifica o servidor (nunca um e-mail pessoal).
        // A biblioteca monta a origem só com protocolo e host: para os serviços reais (HTTPS na porta 443, as
        // únicas que aceitamos) é exatamente o esperado; só este servidor de teste usa uma porta própria.
        assertThat(carga.get("aud").asText()).isEqualTo("http://127.0.0.1");
        assertThat(carga.get("sub").asText()).isEqualTo(ASSUNTO);
        long expira = carga.get("exp").asLong();
        assertThat(expira).isBetween(Instant.now().getEpochSecond(), Instant.now().getEpochSecond() + 24 * 3600);

        // A assinatura confere com a chave pública VAPID: o serviço de push aceitaria este cabeçalho.
        Signature verificador = Signature.getInstance("SHA256withECDSA");
        verificador.initVerify(servidorVapid.getPublic());
        verificador.update((partes[0] + "." + partes[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verificador.verify(rawParaDer(Base64.getUrlDecoder().decode(partes[2])))).isTrue();
    }

    // ------------------------------------------------------------------ respostas do serviço de push

    @Test
    void inscricaoQueNaoExisteMaisEhMarcadaComoExpirada() {
        statusDaResposta.set(410);
        assertThat(sender.enviar(endpoint, publica(navegador), authDoNavegador, "{}", false)).isEqualTo(Resultado.EXPIRADA);

        statusDaResposta.set(404);
        assertThat(sender.enviar(endpoint, publica(navegador), authDoNavegador, "{}", false)).isEqualTo(Resultado.EXPIRADA);
    }

    @Test
    void falhaDoServicoNaoApagaAInscricao() {
        statusDaResposta.set(500);
        assertThat(sender.enviar(endpoint, publica(navegador), authDoNavegador, "{}", false)).isEqualTo(Resultado.ERRO);

        statusDaResposta.set(429);
        assertThat(sender.enviar(endpoint, publica(navegador), authDoNavegador, "{}", false)).isEqualTo(Resultado.ERRO);
    }

    @Test
    void servidorForaDoArNaoDerrubaAAplicacao() {
        servidor.stop(0);
        assertThat(sender.enviar(endpoint, publica(navegador), authDoNavegador, "{}", false)).isEqualTo(Resultado.ERRO);
    }

    @Test
    void chavesDoNavegadorInvalidasDaoErroSemLancarExcecao() {
        assertThat(sender.enviar(endpoint, "chave-invalida", "auth-invalido", "{}", false)).isEqualTo(Resultado.ERRO);
    }

    // ------------------------------------------------------------------ chaves (JDK puro, sem a biblioteca)

    private static KeyPair novoPar() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }

    private static byte[] fixo32(BigInteger n) {
        byte[] bruto = n.toByteArray();
        byte[] saida = new byte[32];
        int copiar = Math.min(bruto.length, 32);
        System.arraycopy(bruto, bruto.length - copiar, saida, 32 - copiar, copiar);
        return saida;
    }

    /** Chave pública no formato dos navegadores: ponto não comprimido (0x04 + X + Y), base64url. */
    private static String publica(KeyPair par) {
        ECPoint w = ((ECPublicKey) par.getPublic()).getW();
        byte[] ponto = new byte[65];
        ponto[0] = 0x04;
        System.arraycopy(fixo32(w.getAffineX()), 0, ponto, 1, 32);
        System.arraycopy(fixo32(w.getAffineY()), 0, ponto, 33, 32);
        return b64(ponto);
    }

    private static String privada(KeyPair par) {
        return b64(fixo32(((ECPrivateKey) par.getPrivate()).getS()));
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** O JWT traz a assinatura como R||S (64 bytes); o Signature do JDK espera DER. */
    private static byte[] rawParaDer(byte[] rs) {
        byte[] r = inteiroDer(new BigInteger(1, java.util.Arrays.copyOfRange(rs, 0, 32)));
        byte[] s = inteiroDer(new BigInteger(1, java.util.Arrays.copyOfRange(rs, 32, 64)));
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(0x30);
        saida.write(r.length + s.length);
        saida.writeBytes(r);
        saida.writeBytes(s);
        return saida.toByteArray();
    }

    private static byte[] inteiroDer(BigInteger n) {
        byte[] v = n.toByteArray();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(0x02);
        saida.write(v.length);
        saida.writeBytes(v);
        return saida.toByteArray();
    }
}
