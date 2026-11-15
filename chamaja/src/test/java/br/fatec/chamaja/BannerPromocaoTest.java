package br.fatec.chamaja;

import br.fatec.chamaja.model.EstadoFila;
import br.fatec.chamaja.model.Estabelecimento;
import br.fatec.chamaja.model.Operador;
import br.fatec.chamaja.model.Promocao;
import br.fatec.chamaja.repository.EstabelecimentoRepository;
import br.fatec.chamaja.repository.OperadorRepository;
import br.fatec.chamaja.repository.PromocaoImagemRepository;
import br.fatec.chamaja.repository.PromocaoRepository;
import br.fatec.chamaja.service.FilaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Banner pronto na promoção: o operador envia a imagem e o cliente vê só ela, sem o cartão padrão. */
@SpringBootTest(properties = {
        "chamaja.seed.enabled=false",
        "chamaja.rate-limit.por-minuto=1000"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BannerPromocaoTest {

    @Autowired MockMvc mvc;
    @Autowired EstabelecimentoRepository estabelecimentos;
    @Autowired OperadorRepository operadores;
    @Autowired PromocaoRepository promocoes;
    @Autowired PromocaoImagemRepository imagens;
    @Autowired PasswordEncoder encoder;
    @Autowired FilaService fila;

    Estabelecimento est;
    String codigo;
    String usuario;

    @BeforeEach
    void preparar() {
        codigo = UUID.randomUUID().toString().substring(0, 12);
        usuario = "op-" + codigo;
        est = new Estabelecimento("Mercado Banner", codigo);
        est.setEstadoFila(EstadoFila.ABERTA);
        est = estabelecimentos.save(est);
        operadores.save(new Operador(est, usuario, encoder.encode("segredo")));
    }

    // ------------------------------------------------------------------ utilidades

    private static byte[] banner(int largura, int altura) throws IOException {
        BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0xDC2626), largura, altura, new Color(0xFACC15)));
        g.fillRect(0, 0, largura, altura);
        g.dispose();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", saida);
        return saida.toByteArray();
    }

    private static MockMultipartFile arquivo(byte[] bytes) {
        return new MockMultipartFile("imagem", "banner.jpg", "image/jpeg", bytes);
    }

    private ResultActions enviar(Long id, String titulo, String preco, MockMultipartFile arquivo, boolean remover)
            throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/painel/promocoes");
        if (arquivo != null) {
            req.file(arquivo);
        }
        req.param("titulo", titulo)
                .param("validade", LocalDate.now().plusDays(5).toString());
        if (id != null) {
            req.param("id", id.toString());
        }
        if (preco != null) {
            req.param("preco", preco);
        }
        if (remover) {
            req.param("removerImagem", "true");
        }
        return mvc.perform(req.with(user(usuario).roles("OPERADOR")).with(csrf()));
    }

    private Promocao unica() {
        var todas = promocoes.findByEstabelecimentoIdOrderByTituloAsc(est.getId());
        assertThat(todas).hasSize(1);
        return todas.get(0);
    }

    private String senhaDoCliente() {
        return fila.retirar(codigo, "Ana").getToken();
    }

    // ------------------------------------------------------------------ operador envia o banner

    @Test
    void comBannerPrecoEDescricaoSaoOpcionais() throws Exception {
        enviar(null, "Encarte da semana", null, arquivo(banner(1200, 630)), false)
                .andExpect(redirectedUrl("/painel/promocoes"));

        Promocao p = unica();
        assertThat(p.getPreco()).isNull();
        assertThat(p.temImagem()).isTrue();
        assertThat(imagens.existsById(p.getId())).isTrue();
    }

    @Test
    void oClienteVeSoOBannerSemPrecoNemTexto() throws Exception {
        enviar(null, "Encarte da semana", null, arquivo(banner(1200, 630)), false);
        Promocao p = unica();

        mvc.perform(get("/senha/" + senhaDoCliente() + "/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promocoes.length()").value(1))
                .andExpect(jsonPath("$.promocoes[0].imagem").value("/promocoes/" + p.getId() + "/imagem?v=" + p.getImagemVersao()))
                .andExpect(jsonPath("$.promocoes[0].preco").doesNotExist())
                .andExpect(jsonPath("$.promocoes[0].largura").value(1000))
                .andExpect(jsonPath("$.promocoes[0].altura").value(525));
    }

    @Test
    void semBannerOCartaoPadraoContinuaIgualEExigeOPreco() throws Exception {
        enviar(null, "Leite 1L", "4.99", null, false).andExpect(redirectedUrl("/painel/promocoes"));

        mvc.perform(get("/senha/" + senhaDoCliente() + "/estado"))
                .andExpect(jsonPath("$.promocoes[0].titulo").value("Leite 1L"))
                .andExpect(jsonPath("$.promocoes[0].preco").value(containsString("4,99")))
                .andExpect(jsonPath("$.promocoes[0].imagem").doesNotExist());
    }

    @Test
    void semBannerESemPrecoEhRecusadoComMensagem() throws Exception {
        enviar(null, "Sem nada", null, null, false)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Informe o preço ou envie o banner")));

        assertThat(promocoes.findByEstabelecimentoIdOrderByTituloAsc(est.getId())).isEmpty();
    }

    @Test
    void arquivoQueNaoEImagemVoltaAoFormularioENaoCriaPromocao() throws Exception {
        MockMultipartFile falso = new MockMultipartFile("imagem", "banner.jpg", "image/jpeg",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));

        enviar(null, "Disfarçado", "9.90", falso, false)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Envie uma imagem JPG ou PNG")));

        assertThat(promocoes.findByEstabelecimentoIdOrderByTituloAsc(est.getId())).isEmpty();
    }

    @Test
    void arquivoMaiorQueOLimiteEhRecusadoComMensagem() throws Exception {
        byte[] grande = new byte[3 * 1024 * 1024 + 1];
        grande[0] = (byte) 0xFF;
        grande[1] = (byte) 0xD8;
        grande[2] = (byte) 0xFF;

        enviar(null, "Pesada", "9.90", arquivo(grande), false)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("maior que 3 MB")));

        assertThat(promocoes.findByEstabelecimentoIdOrderByTituloAsc(est.getId())).isEmpty();
    }

    @Test
    void oBannerGuardadoEOReduzidoEnaoOArquivoOriginal() throws Exception {
        byte[] original = banner(2400, 1260);
        enviar(null, "Grande", null, arquivo(original), false);

        byte[] guardado = imagens.findById(unica().getId()).orElseThrow().getDados();

        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(guardado)).getWidth()).isEqualTo(1000);
        assertThat(guardado).isNotEqualTo(original);
    }

    // ------------------------------------------------------------------ editar

    @Test
    void trocarOBannerMudaAImagemEAVersao() throws Exception {
        enviar(null, "Encarte", null, arquivo(banner(1000, 500)), false);
        Promocao antes = unica();
        byte[] bytesAntes = imagens.findById(antes.getId()).orElseThrow().getDados();
        Thread.sleep(5);

        enviar(antes.getId(), "Encarte", null, arquivo(banner(800, 800)), false)
                .andExpect(redirectedUrl("/painel/promocoes"));

        Promocao depois = unica();
        assertThat(depois.getImagemVersao()).isGreaterThan(antes.getImagemVersao());
        assertThat(depois.getImagemLargura()).isEqualTo(800);
        assertThat(imagens.findById(depois.getId()).orElseThrow().getDados()).isNotEqualTo(bytesAntes);
    }

    @Test
    void editarSemEnviarArquivoMantemOBanner() throws Exception {
        enviar(null, "Encarte", null, arquivo(banner(1000, 500)), false);
        Promocao antes = unica();

        enviar(antes.getId(), "Encarte renomeado", null, null, false)
                .andExpect(redirectedUrl("/painel/promocoes"));

        Promocao depois = unica();
        assertThat(depois.getTitulo()).isEqualTo("Encarte renomeado");
        assertThat(depois.getImagemVersao()).isEqualTo(antes.getImagemVersao());
        assertThat(imagens.existsById(depois.getId())).isTrue();
    }

    @Test
    void removerOBannerVoltaAoCartaoPadraoMasExigeOPreco() throws Exception {
        enviar(null, "Encarte", null, arquivo(banner(1000, 500)), false);
        Promocao p = unica();

        // Sem preço, o cartão padrão não faz sentido: recusa e mantém o banner.
        enviar(p.getId(), "Encarte", null, null, true)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Informe o preço ou envie o banner")));
        assertThat(unica().temImagem()).isTrue();

        enviar(p.getId(), "Encarte", "7.50", null, true).andExpect(redirectedUrl("/painel/promocoes"));

        Promocao depois = unica();
        assertThat(depois.temImagem()).isFalse();
        assertThat(depois.getPreco()).isEqualByComparingTo("7.50");
        assertThat(imagens.existsById(p.getId())).isFalse();
        mvc.perform(get("/promocoes/" + p.getId() + "/imagem")).andExpect(status().isNotFound());
    }

    @Test
    void excluirAPromocaoApagaOBanner() throws Exception {
        enviar(null, "Encarte", null, arquivo(banner(1000, 500)), false);
        Long id = unica().getId();

        mvc.perform(post("/painel/promocoes/" + id + "/excluir").with(user(usuario).roles("OPERADOR")).with(csrf()))
                .andExpect(redirectedUrl("/painel/promocoes"));

        assertThat(promocoes.findById(id)).isEmpty();
        assertThat(imagens.existsById(id)).isFalse();
    }

    @Test
    void oFormularioDeEdicaoMostraOBannerAtualECampoParaRemover() throws Exception {
        enviar(null, "Encarte", null, arquivo(banner(1000, 500)), false);
        Promocao p = unica();

        mvc.perform(get("/painel/promocoes/" + p.getId() + "/editar").with(user(usuario).roles("OPERADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Remover o banner atual")))
                .andExpect(content().string(containsString("/painel/promocoes/" + p.getId() + "/imagem")));

        mvc.perform(get("/painel/promocoes/nova").with(user(usuario).roles("OPERADOR")))
                .andExpect(content().string(not(containsString("Remover o banner atual"))));
    }

    @Test
    void aListaMostraAMiniaturaDoBannerEOCartaoPadrao() throws Exception {
        enviar(null, "Com banner", null, arquivo(banner(1000, 500)), false);
        enviar(null, "Sem banner", "3.00", null, false);

        mvc.perform(get("/painel/promocoes").with(user(usuario).roles("OPERADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("mini-banner")))
                .andExpect(content().string(containsString("Cartão padrão")));
    }

    // ------------------------------------------------------------------ banner público

    @Test
    void bannerPublicoEServidoComCacheEComoJpeg() throws Exception {
        enviar(null, "Encarte", null, arquivo(banner(1000, 500)), false);
        Promocao p = unica();

        mvc.perform(get("/promocoes/" + p.getId() + "/imagem"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", containsString("max-age=")))
                .andExpect(header().string("ETag", "\"" + p.getImagemVersao() + "\""))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).hasSizeGreaterThan(1000));
    }

    @Test
    void navegadorQueJaTemOBannerRecebe304SemBaixarDeNovo() throws Exception {
        enviar(null, "Encarte", null, arquivo(banner(1000, 500)), false);
        Promocao p = unica();

        mvc.perform(get("/promocoes/" + p.getId() + "/imagem").header("If-None-Match", "\"" + p.getImagemVersao() + "\""))
                .andExpect(status().isNotModified());
    }

    @Test
    void bannerDePromocaoDesativadaNaoEhPublico() throws Exception {
        enviar(null, "Encarte", null, arquivo(banner(1000, 500)), false);
        Promocao p = unica();

        mvc.perform(post("/painel/promocoes/" + p.getId() + "/ativa").with(user(usuario).roles("OPERADOR")).with(csrf()));

        mvc.perform(get("/promocoes/" + p.getId() + "/imagem")).andExpect(status().isNotFound());
        // O operador ainda vê a prévia no painel, para poder reativar.
        mvc.perform(get("/painel/promocoes/" + p.getId() + "/imagem").with(user(usuario).roles("OPERADOR")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void bannerDePromocaoVencidaNaoEhPublico() throws Exception {
        enviar(null, "Encarte", null, arquivo(banner(1000, 500)), false);
        Promocao p = unica();
        p.setValidade(LocalDate.now().minusDays(1));
        promocoes.save(p);

        mvc.perform(get("/promocoes/" + p.getId() + "/imagem")).andExpect(status().isNotFound());
        mvc.perform(get("/senha/" + senhaDoCliente() + "/estado")).andExpect(jsonPath("$.promocoes.length()").value(0));
    }

    @Test
    void semBannerOuInexistenteDa404() throws Exception {
        enviar(null, "Sem banner", "3.00", null, false);

        mvc.perform(get("/promocoes/" + unica().getId() + "/imagem")).andExpect(status().isNotFound());
        mvc.perform(get("/promocoes/999999999/imagem")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ segurança do painel

    @Test
    void previaDoPainelNaoMostraBannerDeOutroMercado() throws Exception {
        enviar(null, "Encarte", null, arquivo(banner(1000, 500)), false);
        Long id = unica().getId();

        Estabelecimento outro = new Estabelecimento("Outro Mercado", UUID.randomUUID().toString().substring(0, 12));
        outro = estabelecimentos.save(outro);
        String usuarioDeOutro = "op-outro-" + UUID.randomUUID().toString().substring(0, 6);
        operadores.save(new Operador(outro, usuarioDeOutro, encoder.encode("segredo")));

        mvc.perform(get("/painel/promocoes/" + id + "/imagem").with(user(usuarioDeOutro).roles("OPERADOR")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/painel/promocoes/" + id + "/editar").with(user(usuarioDeOutro).roles("OPERADOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void semLoginNaoEnviaNemVeAPreviaDoPainel() throws Exception {
        mvc.perform(multipart("/painel/promocoes").file(arquivo(new byte[]{1, 2, 3}))
                        .param("titulo", "x").param("validade", "2030-01-01").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/painel/promocoes/1/imagem"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void envioSemTokenAntiCsrfEhRecusado() throws Exception {
        mvc.perform(multipart("/painel/promocoes").file(arquivo(banner(100, 50)))
                        .param("titulo", "x").param("validade", "2030-01-01")
                        .with(user(usuario).roles("OPERADOR")))
                .andExpect(status().isForbidden());
    }
}
