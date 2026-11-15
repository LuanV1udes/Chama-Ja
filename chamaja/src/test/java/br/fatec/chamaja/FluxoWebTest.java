package br.fatec.chamaja;

import br.fatec.chamaja.model.EstadoFila;
import br.fatec.chamaja.model.Estabelecimento;
import br.fatec.chamaja.model.Operador;
import br.fatec.chamaja.repository.EstabelecimentoRepository;
import br.fatec.chamaja.repository.OperadorRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "chamaja.seed.enabled=false")
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

    String codigo;
    String usuario;

    @BeforeEach
    void preparar() {
        codigo = UUID.randomUUID().toString().substring(0, 12);
        usuario = "op-" + codigo;
        Estabelecimento est = new Estabelecimento("Mercado Web", codigo);
        est.setEstadoFila(EstadoFila.ABERTA);
        est = estabelecimentos.save(est);
        operadores.save(new Operador(est, usuario, encoder.encode("segredo")));
    }

    @Test
    void painelExigeLogin() throws Exception {
        mvc.perform(get("/painel"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void paginaDoClienteEPublica() throws Exception {
        mvc.perform(get("/q/" + codigo))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Mercado Web")));
    }

    @Test
    void qrCodeInexistenteDa404() throws Exception {
        mvc.perform(get("/q/naoexiste")).andExpect(status().isNotFound());
    }

    @Test
    void clienteRetiraSenhaEAcompanhaOEstado() throws Exception {
        MvcResult resultado = mvc.perform(post("/q/" + codigo + "/senha").param("nome", "Ana").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String destino = resultado.getResponse().getRedirectedUrl();
        assertThat(destino).startsWith("/senha/");
        String token = destino.substring("/senha/".length());

        mvc.perform(get(destino)).andExpect(status().isOk());
        mvc.perform(get("/senha/" + token + "/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("001"))
                .andExpect(jsonPath("$.status").value("AGUARDANDO"))
                .andExpect(jsonPath("$.posicao").value(1))
                .andExpect(jsonPath("$.proximo").value(true));
    }

    @Test
    void umAparelhoSoSegurUmaSenhaAtiva() throws Exception {
        MvcResult primeira = mvc.perform(post("/q/" + codigo + "/senha").param("nome", "Ana").with(csrf()))
                .andReturn();
        String destino = primeira.getResponse().getRedirectedUrl();
        String token = destino.substring("/senha/".length());
        Cookie cookie = new Cookie("chamaja_" + codigo, token);

        // Mesmo aparelho (cookie) tentando pegar outra senha: volta para a mesma.
        mvc.perform(post("/q/" + codigo + "/senha").param("nome", "Ana de novo").cookie(cookie).with(csrf()))
                .andExpect(redirectedUrl(destino));
        mvc.perform(get("/q/" + codigo).cookie(cookie))
                .andExpect(redirectedUrl(destino));
    }

    @Test
    void postSemCsrfEhRecusado() throws Exception {
        mvc.perform(post("/q/" + codigo + "/senha").param("nome", "Ana"))
                .andExpect(status().isForbidden());
    }

    @Test
    void operadorLogadoChamaAProximaEOClienteVeAChamada() throws Exception {
        MvcResult resultado = mvc.perform(post("/q/" + codigo + "/senha").param("nome", "Ana").with(csrf()))
                .andReturn();
        String token = resultado.getResponse().getRedirectedUrl().substring("/senha/".length());

        mvc.perform(post("/painel/chamar").with(user(usuario).roles("OPERADOR")).with(csrf()))
                .andExpect(redirectedUrl("/painel"));

        mvc.perform(get("/senha/" + token + "/estado"))
                .andExpect(jsonPath("$.status").value("CHAMADA"))
                .andExpect(jsonPath("$.senhaAtual").value("001"));

        mvc.perform(get("/painel").with(user(usuario).roles("OPERADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ana")));
    }

    @Test
    void operadorGeraOQrCodeEmPng() throws Exception {
        mvc.perform(get("/painel/qr.png").with(user(usuario).roles("OPERADOR")))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).hasSizeGreaterThan(100));
    }

    @Test
    void operadorCadastraPromocaoEClienteSoVeAAtivaEVigente() throws Exception {
        var op = user(usuario).roles("OPERADOR");

        mvc.perform(post("/painel/promocoes").with(op).with(csrf())
                        .param("titulo", "Leite 1L")
                        .param("descricao", "Integral")
                        .param("preco", "4.99")
                        .param("validade", java.time.LocalDate.now().plusDays(3).toString()))
                .andExpect(redirectedUrl("/painel/promocoes"));
        mvc.perform(post("/painel/promocoes").with(op).with(csrf())
                        .param("titulo", "Vencida")
                        .param("preco", "1.00")
                        .param("validade", java.time.LocalDate.now().minusDays(1).toString()))
                .andExpect(redirectedUrl("/painel/promocoes"));

        String token = mvc.perform(post("/q/" + codigo + "/senha").param("nome", "Ana").with(csrf()))
                .andReturn().getResponse().getRedirectedUrl().substring("/senha/".length());

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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Informe o título")));
    }
}
