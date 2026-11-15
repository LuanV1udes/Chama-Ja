package br.fatec.chamaja;

import br.fatec.chamaja.service.EnderecoPush;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** O servidor faz requisições para o endereço que o cliente informa: só os serviços de push são aceitos (SSRF). */
class EnderecoPushTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://fcm.googleapis.com/fcm/send/abc123",
            "https://android.googleapis.com/gcm/send/abc",
            "https://updates.push.services.mozilla.com/wpush/v2/abc",
            "https://web.push.apple.com/QGxyz",
            "https://wns2-sn3p.notify.windows.com/w/?token=abc",
            "https://fcm.googleapis.com:443/fcm/send/abc"
    })
    void aceitaOsServicosDePushDosNavegadores(String endpoint) {
        assertThat(EnderecoPush.valido(endpoint)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://fcm.googleapis.com/fcm/send/abc",                 // sem HTTPS
            "https://localhost/push",                                  // máquina local
            "https://127.0.0.1/push",
            "https://169.254.169.254/latest/meta-data/",              // metadados da nuvem
            "https://10.0.0.5/push",                                   // rede interna
            "https://chama-ja.railway.internal/push",                  // rede privada da Railway
            "https://fcm.googleapis.com.evil.com/fcm/send/abc",       // domínio parecido
            "https://evilfcm.googleapis.com.attacker.net/x",
            "https://notfcm.googleapis.com/x",                         // outro host do mesmo domínio-pai
            "https://fcm.googleapis.com@evil.com/fcm/send/abc",       // usuário falso na URL
            "https://user:senha@fcm.googleapis.com/x",
            "https://fcm.googleapis.com:8443/x",                       // porta fora do padrão
            "ftp://fcm.googleapis.com/x",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "https:///sem-host",
            "isso nao e uma url",
            ""
    })
    void recusaQualquerOutroEndereco(String endpoint) {
        assertThat(EnderecoPush.valido(endpoint)).isFalse();
    }

    @Test
    void recusaNuloEEnderecoGigante() {
        assertThat(EnderecoPush.valido(null)).isFalse();
        assertThat(EnderecoPush.valido("https://fcm.googleapis.com/" + "a".repeat(1100))).isFalse();
    }

    @Test
    void maiusculasNoHostNaoEnganam() {
        assertThat(EnderecoPush.valido("https://FCM.GoogleAPIs.com/fcm/send/abc")).isTrue();
        assertThat(EnderecoPush.valido("https://FCM.GoogleAPIs.com.Evil.com/x")).isFalse();
    }

    // ------------------------------------------------------------------ chaves

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] pontoP256() {
        byte[] ponto = new byte[65];
        ponto[0] = 0x04;
        return ponto;
    }

    @Test
    void aceitaChavesNoFormatoDoNavegador() {
        assertThat(EnderecoPush.chavesValidas(b64(pontoP256()), b64(new byte[16]))).isTrue();
    }

    @Test
    void recusaChavesForaDoFormato() {
        byte[] semMarcador = pontoP256();
        semMarcador[0] = 0x02;

        assertThat(EnderecoPush.chavesValidas(b64(semMarcador), b64(new byte[16]))).isFalse();   // não é ponto não comprimido
        assertThat(EnderecoPush.chavesValidas(b64(new byte[64]), b64(new byte[16]))).isFalse();  // tamanho errado
        assertThat(EnderecoPush.chavesValidas(b64(pontoP256()), b64(new byte[8]))).isFalse();    // auth curto
        assertThat(EnderecoPush.chavesValidas("%%%não-é-base64%%%", b64(new byte[16]))).isFalse();
        assertThat(EnderecoPush.chavesValidas(null, null)).isFalse();
    }
}
