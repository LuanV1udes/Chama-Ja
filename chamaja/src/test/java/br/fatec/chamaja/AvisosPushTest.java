package br.fatec.chamaja;

import br.fatec.chamaja.model.EstadoFila;
import br.fatec.chamaja.model.Estabelecimento;
import br.fatec.chamaja.repository.EstabelecimentoRepository;
import br.fatec.chamaja.repository.InscricaoPushRepository;
import br.fatec.chamaja.service.AvisosPushService;
import br.fatec.chamaja.service.FilaException;
import br.fatec.chamaja.service.FilaService;
import br.fatec.chamaja.service.PushSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Avisos no celular: inscrição, momentos do envio, limpeza e segurança (com um enviador de mentira). */
@SpringBootTest(properties = {
        "chamaja.seed.enabled=false",
        "chamaja.rate-limit.por-minuto=1000",
        "chamaja.push.publica=chave-publica-de-teste",
        "chamaja.push.privada=chave-privada-de-teste"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(AvisosPushTest.FalsoConfig.class)
class AvisosPushTest {

    /** Guarda cada aviso em vez de enviar de verdade. */
    static class EnviadorFalso implements PushSender {
        record Aviso(String endpoint, String payload, boolean urgente) {
        }

        final List<Aviso> avisos = new CopyOnWriteArrayList<>();
        volatile Resultado resultado = Resultado.ENVIADA;

        @Override
        public Resultado enviar(String endpoint, String p256dh, String auth, String payload, boolean urgente) {
            avisos.add(new Aviso(endpoint, payload, urgente));
            return resultado;
        }

        List<Aviso> para(String endpoint) {
            return avisos.stream().filter(a -> a.endpoint().equals(endpoint)).toList();
        }
    }

    @TestConfiguration
    static class FalsoConfig {
        @Bean
        @Primary
        EnviadorFalso enviadorFalso() {
            return new EnviadorFalso();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired AvisosPushService push;
    @Autowired FilaService fila;
    @Autowired EstabelecimentoRepository estabelecimentos;
    @Autowired InscricaoPushRepository inscricoes;
    @Autowired EnviadorFalso enviador;

    Estabelecimento est;
    String codigo;

    @BeforeEach
    void preparar() {
        push.executarNaMesmaThread();
        enviador.avisos.clear();
        enviador.resultado = PushSender.Resultado.ENVIADA;
        codigo = UUID.randomUUID().toString().substring(0, 12);
        est = new Estabelecimento("Mercado Push", codigo);
        est.setEstadoFila(EstadoFila.ABERTA);
        est = estabelecimentos.save(est);
    }

    // ------------------------------------------------------------------ utilidades

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String p256dh() {
        byte[] ponto = new byte[65];
        ponto[0] = 0x04;
        return b64(ponto);
    }

    private static String auth() {
        return b64(new byte[16]);
    }

    private static String novoEndpoint() {
        return "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
    }

    private String retirar(String nome) {
        return fila.retirar(codigo, nome).getToken();
    }

    /** Retira a senha, inscreve o aparelho e devolve o endpoint dele. */
    private String senhaComPush(String nome, List<String> tokens) {
        String token = retirar(nome);
        String endpoint = novoEndpoint();
        push.inscrever(token, endpoint, p256dh(), auth());
        tokens.add(token);
        return endpoint;
    }

    private String json(String endpoint) {
        return "{\"endpoint\":\"" + endpoint + "\",\"expirationTime\":null,\"keys\":{\"p256dh\":\""
                + p256dh() + "\",\"auth\":\"" + auth() + "\"}}";
    }

    // ------------------------------------------------------------------ inscrição

    @Test
    void inscreverSalvaELigaAoAparelhoEConfirmaComUmAviso() {
        String token = retirar("Ana");
        String endpoint = novoEndpoint();

        var inscrita = push.inscrever(token, endpoint, p256dh(), auth());
        push.enviarConfirmacao(inscrita);

        assertThat(inscrita.nova()).isTrue();
        assertThat(inscricoes.findBySenhaId(fila.buscarSenha(token).orElseThrow().getId())).isPresent();
        assertThat(enviador.para(endpoint)).hasSize(1);
        assertThat(enviador.para(endpoint).get(0).payload())
                .contains("Avisos ativados").contains("\"forcar\":true").contains("/senha/" + token);
    }

    @Test
    void inscreverDeNovoComOMesmoAparelhoNaoDuplicaNemRepeteAConfirmacao() {
        String token = retirar("Ana");
        String endpoint = novoEndpoint();
        push.inscrever(token, endpoint, p256dh(), auth());

        var segunda = push.inscrever(token, endpoint, p256dh(), auth());

        assertThat(segunda.nova()).isFalse();
        // Continua uma única inscrição para esta loja (as de outros testes ficam em outros estabelecimentos).
        assertThat(inscricoes.findAll().stream()
                .filter(i -> i.getSenha().getEstabelecimento().getId().equals(est.getId())))
                .hasSize(1);
    }

    @Test
    void umAparelhoNovoSubstituiOAnteriorDaMesmaSenha() {
        String token = retirar("Ana");
        push.inscrever(token, novoEndpoint(), p256dh(), auth());
        String novo = novoEndpoint();

        push.inscrever(token, novo, p256dh(), auth());

        var daSenha = inscricoes.findBySenhaId(fila.buscarSenha(token).orElseThrow().getId()).orElseThrow();
        assertThat(daSenha.getEndpoint()).isEqualTo(novo);
    }

    @Test
    void oMesmoAparelhoPassaParaASenhaNova() {
        String endpoint = novoEndpoint();
        String primeira = retirar("Ana");
        push.inscrever(primeira, endpoint, p256dh(), auth());
        fila.cancelar(primeira);

        String segunda = retirar("Ana de novo");
        var inscrita = push.inscrever(segunda, endpoint, p256dh(), auth());

        assertThat(inscrita.nova()).isTrue();
        assertThat(inscricoes.findBySenhaId(fila.buscarSenha(segunda).orElseThrow().getId())).isPresent();
    }

    @Test
    void recusaEnderecosForaDosServicosDePush() {
        String token = retirar("Ana");

        for (String ruim : List.of("http://fcm.googleapis.com/x", "https://localhost/x",
                "https://169.254.169.254/x", "https://fcm.googleapis.com.evil.com/x")) {
            assertThatThrownBy(() -> push.inscrever(token, ruim, p256dh(), auth()))
                    .isInstanceOf(FilaException.class).hasMessageContaining("não aceito");
        }
        assertThat(inscricoes.findBySenhaId(fila.buscarSenha(token).orElseThrow().getId())).isEmpty();
    }

    @Test
    void recusaChavesInvalidas() {
        String token = retirar("Ana");

        assertThatThrownBy(() -> push.inscrever(token, novoEndpoint(), "lixo", auth()))
                .isInstanceOf(FilaException.class).hasMessageContaining("Chaves");
    }

    @Test
    void recusaSenhaJaEncerradaOuInexistente() {
        String token = retirar("Ana");
        fila.cancelar(token);

        assertThatThrownBy(() -> push.inscrever(token, novoEndpoint(), p256dh(), auth()))
                .isInstanceOf(FilaException.class).hasMessageContaining("encerrada");
        assertThatThrownBy(() -> push.inscrever("nao-existe", novoEndpoint(), p256dh(), auth()))
                .isInstanceOf(FilaException.class);
    }

    // ------------------------------------------------------------------ quando avisar

    @Test
    void quandoASenhaEChamadaOClienteRecebeEstaNaSuaVez() {
        List<String> tokens = new ArrayList<>();
        String endpoint = senhaComPush("Ana", tokens);

        fila.chamarProxima(est.getId());

        var avisos = enviador.para(endpoint);
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0).payload()).contains("É a sua vez!").contains("Senha 001").contains("/senha/" + tokens.get(0));
        assertThat(avisos.get(0).urgente()).isTrue();
    }

    @Test
    void repetirAChamadaAvisaDeNovo() {
        List<String> tokens = new ArrayList<>();
        String endpoint = senhaComPush("Ana", tokens);
        fila.chamarProxima(est.getId());

        fila.repetir(est.getId());

        var avisos = enviador.para(endpoint);
        assertThat(avisos).hasSize(2);
        assertThat(avisos.get(1).payload()).contains("Estamos chamando a sua senha");
    }

    @Test
    void quemEstaAAteTresNumerosRecebeOAvisoDeProximidadeUmaUnicaVez() {
        List<String> tokens = new ArrayList<>();
        List<String> endpoints = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            endpoints.add(senhaComPush("Cliente " + i, tokens));
        }

        fila.chamarProxima(est.getId());   // chama a 001: 002, 003 e 004 estão a até 3 números

        for (int i : new int[]{1, 2, 3}) {   // 002, 003, 004
            assertThat(enviador.para(endpoints.get(i))).as("senha %03d", i + 1).hasSize(1);
            assertThat(enviador.para(endpoints.get(i)).get(0).payload()).contains("Sua vez está chegando!");
            assertThat(enviador.para(endpoints.get(i)).get(0).urgente()).isFalse();
        }
        assertThat(enviador.para(endpoints.get(4))).as("005 ainda está longe").isEmpty();
        assertThat(enviador.para(endpoints.get(5))).as("006 ainda está longe").isEmpty();

        fila.chamarProxima(est.getId());   // chama a 002: agora a 005 entra na faixa

        assertThat(enviador.para(endpoints.get(4))).hasSize(1);
        assertThat(enviador.para(endpoints.get(4)).get(0).payload()).contains("Sua vez está chegando!");
        // Quem já foi avisado não recebe de novo: 003 e 004 seguem com um aviso só de proximidade
        // (a 003 ainda recebe o "é a sua vez" depois, quando for chamada).
        assertThat(enviador.para(endpoints.get(2))).hasSize(1);
        assertThat(enviador.para(endpoints.get(3))).hasSize(1);
    }

    @Test
    void quemNaoAtivouOPushNaoRecebeNada() {
        retirar("Ana");   // sem inscrição

        fila.chamarProxima(est.getId());

        assertThat(enviador.avisos).isEmpty();
    }

    @Test
    void naoAvisaUmaSenhaQueJaNaoEstaMaisValida() {
        List<String> tokens = new ArrayList<>();
        String endpoint = senhaComPush("Ana", tokens);
        fila.cancelar(tokens.get(0));

        assertThatThrownBy(() -> fila.chamarProxima(est.getId())).isInstanceOf(FilaException.class);

        assertThat(enviador.para(endpoint)).isEmpty();
    }

    // ------------------------------------------------------------------ limpeza e privacidade

    @Test
    void asInscricoesSomemQuandoASenhaTermina() {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            senhaComPush("Cliente " + i, tokens);
        }
        Long idFinalizada = fila.buscarSenha(tokens.get(0)).orElseThrow().getId();
        Long idPulada = fila.buscarSenha(tokens.get(1)).orElseThrow().getId();
        Long idCancelada = fila.buscarSenha(tokens.get(2)).orElseThrow().getId();
        Long idAguardando = fila.buscarSenha(tokens.get(3)).orElseThrow().getId();

        fila.cancelar(tokens.get(2));                 // cancelada
        fila.chamarProxima(est.getId());              // chama a 001
        fila.finalizar(est.getId());                  // finalizada
        fila.chamarProxima(est.getId());              // chama a 002
        fila.pular(est.getId());                      // pulada

        assertThat(inscricoes.findBySenhaId(idFinalizada)).isEmpty();
        assertThat(inscricoes.findBySenhaId(idPulada)).isEmpty();
        assertThat(inscricoes.findBySenhaId(idCancelada)).isEmpty();
        assertThat(inscricoes.findBySenhaId(idAguardando)).as("quem ainda aguarda mantém o aviso").isPresent();
    }

    @Test
    void chamarOProximoEncerraAAnteriorEApagaASuaInscricao() {
        List<String> tokens = new ArrayList<>();
        senhaComPush("Ana", tokens);
        senhaComPush("Bruno", tokens);
        Long idPrimeira = fila.buscarSenha(tokens.get(0)).orElseThrow().getId();
        Long idSegunda = fila.buscarSenha(tokens.get(1)).orElseThrow().getId();

        fila.chamarProxima(est.getId());
        fila.chamarProxima(est.getId());

        assertThat(inscricoes.findBySenhaId(idPrimeira)).isEmpty();
        assertThat(inscricoes.findBySenhaId(idSegunda)).isPresent();   // a chamada atual ainda pode ser repetida
    }

    @Test
    void zerarAFilaApagaTodasAsInscricoes() {
        List<String> tokens = new ArrayList<>();
        senhaComPush("Ana", tokens);
        senhaComPush("Bruno", tokens);
        Long idAna = fila.buscarSenha(tokens.get(0)).orElseThrow().getId();
        Long idBruno = fila.buscarSenha(tokens.get(1)).orElseThrow().getId();
        assertThat(inscricoes.findBySenhaId(idAna)).isPresent();

        fila.zerar(est.getId());

        // As senhas foram apagadas e as inscrições vão junto (ON DELETE CASCADE).
        assertThat(inscricoes.findBySenhaId(idAna)).isEmpty();
        assertThat(inscricoes.findBySenhaId(idBruno)).isEmpty();
    }

    @Test
    void inscricaoExpiradaNoServicoDePushEEsquecida() {
        List<String> tokens = new ArrayList<>();
        senhaComPush("Ana", tokens);
        Long id = fila.buscarSenha(tokens.get(0)).orElseThrow().getId();
        enviador.resultado = PushSender.Resultado.EXPIRADA;

        fila.chamarProxima(est.getId());

        assertThat(inscricoes.findBySenhaId(id)).isEmpty();
    }

    @Test
    void falhaPassageiraNaoApagaAInscricao() {
        List<String> tokens = new ArrayList<>();
        senhaComPush("Ana", tokens);
        Long id = fila.buscarSenha(tokens.get(0)).orElseThrow().getId();
        enviador.resultado = PushSender.Resultado.ERRO;

        fila.chamarProxima(est.getId());

        assertThat(inscricoes.findBySenhaId(id)).isPresent();
    }

    // ------------------------------------------------------------------ pela web

    @Test
    void inscreverPelaWebRetorna204SalvaEConfirma() throws Exception {
        String token = retirar("Ana");
        String endpoint = novoEndpoint();

        mvc.perform(post("/senha/" + token + "/push").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(endpoint)))
                .andExpect(status().isNoContent());

        assertThat(inscricoes.findBySenhaId(fila.buscarSenha(token).orElseThrow().getId())).isPresent();
        assertThat(enviador.para(endpoint)).hasSize(1);
    }

    @Test
    void inscreverSemTokenAntiCsrfEhRecusado() throws Exception {
        String token = retirar("Ana");

        mvc.perform(post("/senha/" + token + "/push")
                        .contentType(MediaType.APPLICATION_JSON).content(json(novoEndpoint())))
                .andExpect(status().isForbidden());
    }

    @Test
    void enderecoDeServidorInternoRetorna400() throws Exception {
        String token = retirar("Ana");

        mvc.perform(post("/senha/" + token + "/push").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("https://169.254.169.254/latest/meta-data")))
                .andExpect(status().isBadRequest());
        assertThat(enviador.avisos).isEmpty();
    }

    @Test
    void corpoSemChavesRetorna400() throws Exception {
        String token = retirar("Ana");

        mvc.perform(post("/senha/" + token + "/push").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + novoEndpoint() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void senhaDesconhecidaRetorna404() throws Exception {
        mvc.perform(post("/senha/nao-existe/push").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(novoEndpoint())))
                .andExpect(status().isNotFound());
    }

    @Test
    void aPaginaDaSenhaTrazAChavePublicaEOManifesto() throws Exception {
        String token = retirar("Ana");

        mvc.perform(get("/senha/" + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-push-chave=\"chave-publica-de-teste\"")))
                .andExpect(content().string(containsString("rel=\"manifest\"")))
                .andExpect(content().string(containsString("/senha/" + token + "/manifest.webmanifest")))
                .andExpect(content().string(containsString("Permitir notificações")));
    }

    @Test
    void oManifestoAbreOIconeInstaladoDiretoNaSenha() throws Exception {
        String token = retirar("Ana");

        mvc.perform(get("/senha/" + token + "/manifest.webmanifest"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("application/manifest+json")))
                .andExpect(jsonPath("$.start_url").value("/senha/" + token))
                .andExpect(jsonPath("$.display").value("standalone"))
                .andExpect(jsonPath("$.icons.length()").value(2));

        mvc.perform(get("/senha/nao-existe/manifest.webmanifest")).andExpect(status().isNotFound());
    }

    @Test
    void oServiceWorkerEOsIconesSaoServidos() throws Exception {
        mvc.perform(get("/sw.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("addEventListener('push'")));
        mvc.perform(get("/img/icone-192.png")).andExpect(status().isOk());
        mvc.perform(get("/img/icone-512.png")).andExpect(status().isOk());
        mvc.perform(get("/img/badge-96.png")).andExpect(status().isOk());
    }
}
