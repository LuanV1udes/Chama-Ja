package br.fatec.chamaja;

import br.fatec.chamaja.model.EstadoFila;
import br.fatec.chamaja.model.Estabelecimento;
import br.fatec.chamaja.model.Operador;
import br.fatec.chamaja.repository.EstabelecimentoRepository;
import br.fatec.chamaja.repository.OperadorRepository;
import br.fatec.chamaja.service.FilaService;
import br.fatec.chamaja.service.QrAcessoService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "chamaja.seed.enabled=false",
        // Muitas requisições no mesmo minuto, todas do mesmo IP de teste.
        "chamaja.rate-limit.por-minuto=1000"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class FluxoWebTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    EstabelecimentoRepository estabelecimentos;

    @Autowired
    OperadorRepository operadores;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    QrAcessoService qr;

    @Autowired
    FilaService fila;

    Estabelecimento est;
    String codigo;
    String usuario;

    @BeforeEach
    void preparar() {
        codigo = UUID.randomUUID().toString().substring(0, 12);
        usuario = "op-" + codigo;
        est = new Estabelecimento("Mercado Web", codigo);
        est.setEstadoFila(EstadoFila.ABERTA);
        est = estabelecimentos.save(est);
        operadores.save(new Operador(est, usuario, encoder.encode("segredo")));
    }

    /** Escaneia o QR com um código válido e devolve o cookie de permissão que o aparelho recebe. */
    private Cookie escanear() throws Exception {
        MvcResult resultado = mvc.perform(get("/q/" + codigo).param("t", qr.tokenAtual(codigo)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/q/" + codigo))
                .andReturn();
        Cookie permissao = resultado.getResponse().getCookie("chamaja_ok_" + codigo);
        assertThat(permissao).isNotNull();
        return permissao;
    }

    /** Escaneia o QR, pega a senha e devolve o token dela. */
    private String retirarSenha(String nome) throws Exception {
        Cookie permissao = escanear();
        MvcResult resultado = mvc.perform(post("/q/" + codigo + "/senha").param("nome", nome)
                        .cookie(permissao).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String destino = resultado.getResponse().getRedirectedUrl();
        assertThat(destino).startsWith("/senha/");
        return destino.substring("/senha/".length());
    }

    // ------------------------------------------------------------------ acesso e QR rotativo

    @Test
    void painelExigeLogin() throws Exception {
        mvc.perform(get("/painel"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void semEscanearOQrAPaginaPedeParaEscanearENaoMostraOFormulario() throws Exception {
        mvc.perform(get("/q/" + codigo))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mercado Web")))
                .andExpect(content().string(containsString("Escaneie o QR code na entrada")))
                .andExpect(content().string(not(containsString("Pegar minha senha"))));
    }

    @Test
    void qrComCodigoValidoLiberaOFormulario() throws Exception {
        Cookie permissao = escanear();

        mvc.perform(get("/q/" + codigo).cookie(permissao))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pegar minha senha")));
    }

    @Test
    void qrComCodigoInvalidoOuExpiradoNaoLibera() throws Exception {
        MvcResult resultado = mvc.perform(get("/q/" + codigo).param("t", "codigo-que-nao-existe"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("expirou")))
                .andExpect(content().string(not(containsString("Pegar minha senha"))))
                .andReturn();
        assertThat(resultado.getResponse().getCookie("chamaja_ok_" + codigo)).isNull();
    }

    @Test
    void codigoDeOutroEstabelecimentoNaoServe() throws Exception {
        MvcResult resultado = mvc.perform(get("/q/" + codigo).param("t", qr.tokenAtual("outro-estabelecimento")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("expirou")))
                .andReturn();
        assertThat(resultado.getResponse().getCookie("chamaja_ok_" + codigo)).isNull();
    }

    @Test
    void retirarSenhaSemTerEscaneadoEhRecusado() throws Exception {
        mvc.perform(post("/q/" + codigo + "/senha").param("nome", "Ana").with(csrf()))
                .andExpect(redirectedUrl("/q/" + codigo))
                .andExpect(flash().attribute("erro", containsString("Escaneie o QR code")));

        assertThat(fila.listar(est.getId())).isEmpty();
    }

    @Test
    void permissaoDoQrServeUmaUnicaVez() throws Exception {
        Cookie permissao = escanear();

        mvc.perform(post("/q/" + codigo + "/senha").param("nome", "Ana").cookie(permissao).with(csrf()))
                .andExpect(status().is3xxRedirection());

        // Quem guardou o cookie e tenta pegar outra senha (sem o cookie da senha) não consegue.
        mvc.perform(post("/q/" + codigo + "/senha").param("nome", "Ana de novo").cookie(permissao).with(csrf()))
                .andExpect(redirectedUrl("/q/" + codigo))
                .andExpect(flash().attribute("erro", containsString("Escaneie o QR code")));

        assertThat(fila.listar(est.getId())).hasSize(1);
    }

    @Test
    void errarONomeNaoGastaAPermissao() throws Exception {
        Cookie permissao = escanear();

        mvc.perform(post("/q/" + codigo + "/senha").param("nome", "   ").cookie(permissao).with(csrf()))
                .andExpect(redirectedUrl("/q/" + codigo))
                .andExpect(flash().attribute("erro", containsString("nome")));

        mvc.perform(post("/q/" + codigo + "/senha").param("nome", "Ana").cookie(permissao).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/senha/")));
    }

    @Test
    void postSemCsrfEhRecusado() throws Exception {
        mvc.perform(post("/q/" + codigo + "/senha").param("nome", "Ana"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ fluxo do cliente

    @Test
    void clienteRetiraSenhaEAcompanhaOEstado() throws Exception {
        String token = retirarSenha("Ana");

        mvc.perform(get("/senha/" + token)).andExpect(status().isOk());
        mvc.perform(get("/senha/" + token + "/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("001"))
                .andExpect(jsonPath("$.status").value("AGUARDANDO"))
                .andExpect(jsonPath("$.posicao").value(1))
                .andExpect(jsonPath("$.proximo").value(true));
    }

    @Test
    void umAparelhoSoSegurUmaSenhaAtiva() throws Exception {
        String token = retirarSenha("Ana");
        String destino = "/senha/" + token;
        Cookie daSenha = new Cookie("chamaja_" + codigo, token);

        // Mesmo aparelho (cookie da senha) tentando pegar outra: volta para a mesma.
        mvc.perform(post("/q/" + codigo + "/senha").param("nome", "Ana de novo").cookie(daSenha).with(csrf()))
                .andExpect(redirectedUrl(destino));
        mvc.perform(get("/q/" + codigo).cookie(daSenha))
                .andExpect(redirectedUrl(destino));
    }

    @Test
    void operadorLogadoChamaAProximaEOClienteVeAChamada() throws Exception {
        String token = retirarSenha("Ana");

        mvc.perform(post("/painel/chamar").with(user(usuario).roles("OPERADOR")).with(csrf()))
                .andExpect(redirectedUrl("/painel"));

        mvc.perform(get("/senha/" + token + "/estado"))
                .andExpect(jsonPath("$.status").value("CHAMADA"))
                .andExpect(jsonPath("$.senhaAtual").value("001"));

        mvc.perform(get("/painel").with(user(usuario).roles("OPERADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ana")));
    }

    // ------------------------------------------------------------------ push desligado (sem chaves VAPID)

    @Test
    void semChavesVapidAPaginaNaoOfereceOPushENemAceitaInscricao() throws Exception {
        String token = retirarSenha("Ana");

        mvc.perform(get("/senha/" + token))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("data-push-chave"))));

        mvc.perform(post("/senha/" + token + "/push").with(csrf())
                        .contentType("application/json")
                        .content("{\"endpoint\":\"https://fcm.googleapis.com/x\",\"keys\":{\"p256dh\":\"a\",\"auth\":\"b\"}}"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ tela de entrada

    @Test
    void operadorVeOLinkSecretoDaTelaDeEntrada() throws Exception {
        mvc.perform(get("/painel/tela").with(user(usuario).roles("OPERADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/tela/" + est.getCodigoTela())));
    }

    @Test
    void telaDeEntradaNaoPrecisaDeLoginEMostraOQr() throws Exception {
        mvc.perform(get("/tela/" + est.getCodigoTela()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Escaneie para pegar sua senha")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));

        mvc.perform(get("/tela/" + est.getCodigoTela() + "/qr.png"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).hasSizeGreaterThan(100));
    }

    @Test
    void linkDaTelaInexistenteDa404() throws Exception {
        mvc.perform(get("/tela/naoexiste")).andExpect(status().isNotFound());
        mvc.perform(get("/tela/naoexiste/qr.png")).andExpect(status().isNotFound());
        // O código do QR (que aparece na URL do cliente) não abre a tela: são segredos diferentes.
        mvc.perform(get("/tela/" + codigo)).andExpect(status().isNotFound());
    }

    @Test
    void gerarNovoLinkInvalidaOAntigo() throws Exception {
        String antigo = est.getCodigoTela();

        mvc.perform(post("/painel/tela/renovar").with(user(usuario).roles("OPERADOR")).with(csrf()))
                .andExpect(redirectedUrl("/painel/tela"));

        String novo = estabelecimentos.findById(est.getId()).orElseThrow().getCodigoTela();
        assertThat(novo).isNotEqualTo(antigo).hasSize(32);
        mvc.perform(get("/tela/" + antigo)).andExpect(status().isNotFound());
        mvc.perform(get("/tela/" + novo)).andExpect(status().isOk());
    }

    @Test
    void renovarOLinkExigeLogin() throws Exception {
        mvc.perform(post("/painel/tela/renovar").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // ------------------------------------------------------------------ promoções

    @Test
    void operadorCadastraPromocaoEClienteSoVeAAtivaEVigente() throws Exception {
        var op = user(usuario).roles("OPERADOR");

        mvc.perform(post("/painel/promocoes").with(op).with(csrf())
                        .param("titulo", "Leite 1L")
                        .param("descricao", "Integral")
                        .param("preco", "4.99")
                        .param("validade", LocalDate.now().plusDays(3).toString()))
                .andExpect(redirectedUrl("/painel/promocoes"));
        mvc.perform(post("/painel/promocoes").with(op).with(csrf())
                        .param("titulo", "Vencida")
                        .param("preco", "1.00")
                        .param("validade", LocalDate.now().minusDays(1).toString()))
                .andExpect(redirectedUrl("/painel/promocoes"));

        String token = retirarSenha("Ana");

        mvc.perform(get("/senha/" + token + "/estado"))
                .andExpect(jsonPath("$.promocoes.length()").value(1))
                .andExpect(jsonPath("$.promocoes[0].titulo").value("Leite 1L"));
    }

    @Test
    void promocaoComDadosInvalidosVoltaAoFormulario() throws Exception {
        mvc.perform(post("/painel/promocoes").with(user(usuario).roles("OPERADOR")).with(csrf())
                        .param("titulo", "")
                        .param("preco", "4.99"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Informe o título")));
    }
}
