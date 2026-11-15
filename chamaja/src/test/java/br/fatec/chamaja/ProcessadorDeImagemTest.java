package br.fatec.chamaja;

import br.fatec.chamaja.service.ProcessadorDeImagem;
import br.fatec.chamaja.service.ProcessadorDeImagem.ImagemProcessada;
import br.fatec.chamaja.service.PromocaoInvalida;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O banner vem de um arquivo não confiável: só vira JPEG leve, e o que não for imagem de verdade é recusado. */
class ProcessadorDeImagemTest {

    private final ProcessadorDeImagem processador = new ProcessadorDeImagem();

    // ------------------------------------------------------------------ imagens de teste

    private static byte[] gerar(int largura, int altura, int tipo, String formato) throws IOException {
        BufferedImage img = new BufferedImage(largura, altura, tipo);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0xF59E0B), largura, altura, new Color(0x2563EB)));
        g.fillRect(0, 0, largura, altura);
        g.setColor(Color.WHITE);
        g.fillOval(largura / 4, altura / 4, largura / 2, altura / 2);
        g.dispose();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(img, formato, saida);
        return saida.toByteArray();
    }

    private static byte[] jpeg(int l, int a) throws IOException {
        return gerar(l, a, BufferedImage.TYPE_INT_RGB, "jpeg");
    }

    private static byte[] png(int l, int a) throws IOException {
        return gerar(l, a, BufferedImage.TYPE_INT_ARGB, "png");
    }

    private static BufferedImage ler(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private static byte[] texto(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ o que sai

    @Test
    void bannerGrandeEhReduzidoMantendoAProporcao() throws Exception {
        ImagemProcessada r = processador.processar(jpeg(2400, 1260));

        assertThat(r.largura()).isEqualTo(1000);
        assertThat(r.altura()).isEqualTo(525);
        BufferedImage saida = ler(r.jpeg());
        assertThat(saida.getWidth()).isEqualTo(1000);
        assertThat(saida.getHeight()).isEqualTo(525);
        assertThat(r.jpeg().length).isLessThanOrEqualTo(ProcessadorDeImagem.TAMANHO_MAXIMO_SAIDA);
    }

    @Test
    void bannerPequenoNaoEhAmpliado() throws Exception {
        ImagemProcessada r = processador.processar(jpeg(400, 200));

        assertThat(r.largura()).isEqualTo(400);
        assertThat(r.altura()).isEqualTo(200);
    }

    @Test
    void imagemAltaTambemCabeNoLimiteDeAltura() throws Exception {
        ImagemProcessada r = processador.processar(jpeg(600, 2000));

        assertThat(r.altura()).isEqualTo(1000);
        assertThat(r.largura()).isEqualTo(300);
    }

    @Test
    void asaidaEhSempreJpeg() throws Exception {
        for (byte[] entrada : new byte[][]{jpeg(800, 400), png(800, 400)}) {
            byte[] saida = processador.processar(entrada).jpeg();
            assertThat(saida[0] & 0xFF).isEqualTo(0xFF);
            assertThat(saida[1] & 0xFF).isEqualTo(0xD8);
            assertThat(saida[2] & 0xFF).isEqualTo(0xFF);
        }
    }

    @Test
    void pngComTransparenciaViraJpegSobreFundoBranco() throws Exception {
        BufferedImage transparente = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);   // 100% transparente
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(transparente, "png", bytes);

        BufferedImage saida = ler(processador.processar(bytes.toByteArray()).jpeg());

        Color canto = new Color(saida.getRGB(5, 5));
        assertThat(canto.getRed()).isGreaterThanOrEqualTo(250);
        assertThat(canto.getGreen()).isGreaterThanOrEqualTo(250);
        assertThat(canto.getBlue()).isGreaterThanOrEqualTo(250);
    }

    @Test
    void oTipoVemDoConteudoENaoDeUmNomeOuTipoInformado() throws Exception {
        // Um PNG de verdade é aceito, qualquer que seja o nome que o arquivo tinha no computador.
        assertThat(processador.processar(png(300, 150)).largura()).isEqualTo(300);
    }

    // ------------------------------------------------------------------ o que é recusado

    @Test
    void recusaArquivosQueNaoSaoJpgNemPng() {
        byte[][] intrusos = {
                texto("isto é um texto qualquer"),
                texto("<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>"),
                texto("<html><script>alert(1)</script></html>"),
                texto("GIF89a\u0001\u0000\u0001\u0000"),                            // GIF
                texto("RIFF\u0000\u0000\u0000\u0000WEBPVP8 "),                       // WebP
                texto("%PDF-1.7 conteúdo"),                                          // PDF
                new byte[]{'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00},                  // executável do Windows
                texto("BM......"),                                                    // BMP
        };
        for (byte[] intruso : intrusos) {
            assertThatThrownBy(() -> processador.processar(intruso))
                    .isInstanceOf(PromocaoInvalida.class)
                    .hasMessageContaining("JPG ou PNG");
        }
    }

    @Test
    void recusaArquivoVazioOuNulo() {
        assertThatThrownBy(() -> processador.processar(new byte[0]))
                .isInstanceOf(PromocaoInvalida.class).hasMessageContaining("Escolha");
        assertThatThrownBy(() -> processador.processar(null))
                .isInstanceOf(PromocaoInvalida.class).hasMessageContaining("Escolha");
    }

    @Test
    void recusaArquivoMaiorQueTresMegabytes() {
        byte[] grande = new byte[ProcessadorDeImagem.TAMANHO_MAXIMO_BYTES + 1];
        grande[0] = (byte) 0xFF;
        grande[1] = (byte) 0xD8;
        grande[2] = (byte) 0xFF;

        assertThatThrownBy(() -> processador.processar(grande))
                .isInstanceOf(PromocaoInvalida.class).hasMessageContaining("3 MB");
    }

    @Test
    void recusaDimensoesGrandesAntesDeDecodificar() throws Exception {
        // 6000 x 6000 em tons de cinza: o arquivo PNG é pequeno (cor uniforme), mas a imagem seria enorme na memória.
        BufferedImage enorme = new BufferedImage(6000, 6000, BufferedImage.TYPE_BYTE_GRAY);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(enorme, "png", bytes);
        assertThat(bytes.size()).as("o arquivo é leve, o problema é a dimensão").isLessThan(ProcessadorDeImagem.TAMANHO_MAXIMO_BYTES);

        assertThatThrownBy(() -> processador.processar(bytes.toByteArray()))
                .isInstanceOf(PromocaoInvalida.class).hasMessageContaining("dimensões");
    }

    @Test
    void recusaImagemComMuitosPixelsMesmoComLadosPermitidos() throws Exception {
        BufferedImage grande = new BufferedImage(4500, 4000, BufferedImage.TYPE_BYTE_GRAY);   // 18 milhões de pixels
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(grande, "png", bytes);

        assertThatThrownBy(() -> processador.processar(bytes.toByteArray()))
                .isInstanceOf(PromocaoInvalida.class).hasMessageContaining("dimensões");
    }

    @Test
    void recusaArquivoCorrompidoOuTruncado() throws Exception {
        byte[] original = jpeg(800, 400);
        byte[] truncado = java.util.Arrays.copyOf(original, 40);
        byte[] lixoDepoisDoCabecalho = new byte[300];
        lixoDepoisDoCabecalho[0] = (byte) 0xFF;
        lixoDepoisDoCabecalho[1] = (byte) 0xD8;
        lixoDepoisDoCabecalho[2] = (byte) 0xFF;

        for (byte[] ruim : new byte[][]{truncado, lixoDepoisDoCabecalho}) {
            assertThatThrownBy(() -> processador.processar(ruim))
                    .isInstanceOf(PromocaoInvalida.class).hasMessageContaining("Não foi possível ler");
        }
    }

    @Test
    void oErroApontaParaOCampoDaImagem() {
        assertThatThrownBy(() -> processador.processar(texto("nada")))
                .isInstanceOfSatisfying(PromocaoInvalida.class, e -> assertThat(e.getCampo()).isEqualTo("imagem"));
    }

    @Test
    void dadosAnexadosAUmJpegNaoSobrevivem() throws Exception {
        // Um JPEG válido com um "script" colado no fim: o arquivo é regravado, então o anexo some.
        byte[] malicioso = concatenar(jpeg(400, 200), texto("<script>alert('xss')</script><?php system($_GET['c']); ?>"));

        byte[] saida = processador.processar(malicioso).jpeg();

        assertThat(new String(saida, StandardCharsets.ISO_8859_1)).doesNotContain("<script>").doesNotContain("<?php");
    }

    private static byte[] concatenar(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
