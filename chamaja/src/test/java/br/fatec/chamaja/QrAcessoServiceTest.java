package br.fatec.chamaja;

import br.fatec.chamaja.service.QrAcessoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** Regra do QR rotativo, com um relógio controlado (sem esperar minutos de verdade). */
class QrAcessoServiceTest {

    /** Relógio que o teste adianta à vontade. */
    static class RelogioFalso extends Clock {
        private Instant agora;

        RelogioFalso(Instant inicio) {
            this.agora = inicio;
        }

        void avancar(Duration tempo) {
            agora = agora.plus(tempo);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return agora; }
    }

    private static final String LOJA = "loja123";

    private RelogioFalso relogio;
    private QrAcessoService qr;

    @BeforeEach
    void preparar() throws Exception {
        // 12:00:00 exato: começo de uma janela de 60 s.
        relogio = new RelogioFalso(Instant.parse("2026-11-15T12:00:00Z"));
        qr = novo("segredo-de-teste");
    }

    private QrAcessoService novo(String segredo) throws Exception {
        Constructor<QrAcessoService> c = QrAcessoService.class
                .getDeclaredConstructor(String.class, long.class, long.class, Clock.class);
        c.setAccessible(true);
        return c.newInstance(segredo, 60L, 600L, relogio);
    }

    // ------------------------------------------------------------------ código do QR

    @Test
    void codigoDaJanelaAtualEhValido() {
        assertThat(qr.tokenValido(LOJA, qr.tokenAtual(LOJA))).isTrue();
    }

    @Test
    void codigoAindaValeNaJanelaSeguinte() {
        String token = qr.tokenAtual(LOJA);

        relogio.avancar(Duration.ofSeconds(60));

        assertThat(qr.tokenValido(LOJA, token)).isTrue();
    }

    @Test
    void codigoVenceDepoisDeDuasJanelas() {
        String token = qr.tokenAtual(LOJA);

        relogio.avancar(Duration.ofSeconds(120));

        assertThat(qr.tokenValido(LOJA, token)).isFalse();
    }

    @Test
    void ocodigoMudaACadaJanela() {
        String antes = qr.tokenAtual(LOJA);
        relogio.avancar(Duration.ofSeconds(60));

        assertThat(qr.tokenAtual(LOJA)).isNotEqualTo(antes);
    }

    @Test
    void codigoDeOutroEstabelecimentoNaoServe() {
        assertThat(qr.tokenValido("outra-loja", qr.tokenAtual(LOJA))).isFalse();
    }

    @Test
    void codigoForjadoVazioOuNuloNaoServe() {
        assertThat(qr.tokenValido(LOJA, "abc")).isFalse();
        assertThat(qr.tokenValido(LOJA, "")).isFalse();
        assertThat(qr.tokenValido(LOJA, null)).isFalse();
        assertThat(qr.tokenValido(LOJA, "A".repeat(22))).isFalse();
    }

    @Test
    void codigoAssinadoComOutroSegredoNaoServe() throws Exception {
        String deOutroServidor = novo("outro-segredo").tokenAtual(LOJA);

        assertThat(qr.tokenValido(LOJA, deOutroServidor)).isFalse();
    }

    // ------------------------------------------------------------------ permissão do aparelho

    @Test
    void permissaoValeAteExpirar() {
        String permissao = qr.emitirPermissao(LOJA);
        assertThat(qr.permissaoValida(LOJA, permissao)).isTrue();

        relogio.avancar(Duration.ofSeconds(599));
        assertThat(qr.permissaoValida(LOJA, permissao)).isTrue();

        relogio.avancar(Duration.ofSeconds(1));
        assertThat(qr.permissaoValida(LOJA, permissao)).isFalse();
    }

    @Test
    void permissaoDeOutroEstabelecimentoNaoServe() {
        assertThat(qr.permissaoValida("outra-loja", qr.emitirPermissao(LOJA))).isFalse();
    }

    @Test
    void permissaoAdulteradaNaoServe() {
        String permissao = qr.emitirPermissao(LOJA);
        String[] partes = permissao.split("\\.");

        String prazoMaior = (Long.parseLong(partes[0]) + 99999) + "." + partes[1] + "." + partes[2];
        String assinaturaTrocada = partes[0] + "." + partes[1] + "." + "x".repeat(partes[2].length());

        assertThat(qr.permissaoValida(LOJA, prazoMaior)).isFalse();
        assertThat(qr.permissaoValida(LOJA, assinaturaTrocada)).isFalse();
        assertThat(qr.permissaoValida(LOJA, "lixo")).isFalse();
        assertThat(qr.permissaoValida(LOJA, null)).isFalse();
    }

    @Test
    void permissaoServeUmaUnicaVez() {
        String permissao = qr.emitirPermissao(LOJA);

        assertThat(qr.consumirPermissao(LOJA, permissao)).isTrue();
        assertThat(qr.consumirPermissao(LOJA, permissao)).isFalse();
        assertThat(qr.permissaoValida(LOJA, permissao)).isFalse();
    }

    @Test
    void permissoesDiferentesSaoIndependentes() {
        String primeira = qr.emitirPermissao(LOJA);
        String segunda = qr.emitirPermissao(LOJA);

        assertThat(qr.consumirPermissao(LOJA, primeira)).isTrue();
        assertThat(qr.permissaoValida(LOJA, segunda)).isTrue();
    }

    @Test
    void permissaoVencidaNaoPodeSerConsumida() {
        String permissao = qr.emitirPermissao(LOJA);

        relogio.avancar(Duration.ofSeconds(601));

        assertThat(qr.consumirPermissao(LOJA, permissao)).isFalse();
    }
}
