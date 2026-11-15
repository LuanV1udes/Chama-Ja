package br.fatec.chamaja.service;

import org.springframework.stereotype.Component;

import javax.imageio.IIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Prepara o banner enviado pelo operador. O arquivo é de fonte não confiável, então nada dele é
 * guardado como veio:
 *
 * <ul>
 *   <li>o tipo é decidido pelo <b>conteúdo</b> (não pelo nome nem pelo tipo informado): só JPG e PNG;</li>
 *   <li>tamanho e dimensões são limitados antes de decodificar (evita "bomba de descompressão" e falta de memória);</li>
 *   <li>a imagem é decodificada e <b>regravada</b> como JPEG: o que não for pixel (metadados, localização do
 *       celular, dados anexados, scripts) deixa de existir;</li>
 *   <li>é reduzida para caber na tela do celular e comprimida, para carregar rápido com internet fraca.</li>
 * </ul>
 */
@Component
public class ProcessadorDeImagem {

    public static final int TAMANHO_MAXIMO_BYTES = 3 * 1024 * 1024;

    static final int LADO_MAXIMO_ENTRADA = 5000;
    static final long PIXELS_MAXIMOS_ENTRADA = 12_000_000L;
    static final int LARGURA_MAXIMA = 1000;
    static final int ALTURA_MAXIMA = 1000;
    /** Tamanho máximo do banner guardado (depois de reduzido e comprimido). */
    public static final int TAMANHO_MAXIMO_SAIDA = 700 * 1024;
    private static final int[] QUALIDADES = {85, 75, 65, 55};

    public record ImagemProcessada(byte[] jpeg, int largura, int altura) {
    }

    public ImagemProcessada processar(byte[] bruto) {
        if (bruto == null || bruto.length == 0) {
            throw erro("Escolha um arquivo de imagem.");
        }
        if (bruto.length > TAMANHO_MAXIMO_BYTES) {
            throw erro("A imagem é maior que 3 MB. Reduza o arquivo e tente de novo.");
        }
        String formato = formatoPeloConteudo(bruto);
        if (formato == null) {
            throw erro("Envie uma imagem JPG ou PNG.");
        }
        BufferedImage original = decodificar(bruto, formato);
        return codificar(reduzirSobreBranco(original));
    }

    // ------------------------------------------------------------------ tipo pelo conteúdo

    /** "jpeg" ou "png" conforme os primeiros bytes do arquivo; null para qualquer outra coisa. */
    static String formatoPeloConteudo(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        if (b.length >= png.length) {
            for (int i = 0; i < png.length; i++) {
                if (b[i] != png[i]) {
                    return null;
                }
            }
            return "png";
        }
        return null;
    }

    // ------------------------------------------------------------------ decodificar com limites

    private BufferedImage decodificar(byte[] bruto, String formato) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(bruto))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReadersByFormatName(formato);
            if (entrada == null || !leitores.hasNext()) {
                throw erro("Não foi possível ler a imagem. Envie um JPG ou PNG.");
            }
            ImageReader leitor = leitores.next();
            try {
                leitor.setInput(entrada, true, true);
                // Dimensões lidas só do cabeçalho: confere ANTES de alocar a imagem inteira na memória.
                int largura = leitor.getWidth(0);
                int altura = leitor.getHeight(0);
                if (largura <= 0 || altura <= 0) {
                    throw erro("Não foi possível ler a imagem. Envie um JPG ou PNG.");
                }
                if (largura > LADO_MAXIMO_ENTRADA || altura > LADO_MAXIMO_ENTRADA
                        || (long) largura * altura > PIXELS_MAXIMOS_ENTRADA) {
                    throw erro("A imagem tem dimensões grandes demais (máximo " + LADO_MAXIMO_ENTRADA
                            + " pixels de lado). Reduza e tente de novo.");
                }
                BufferedImage imagem = leitor.read(0);
                if (imagem == null) {
                    throw erro("Não foi possível ler a imagem. Envie um JPG ou PNG.");
                }
                return imagem;
            } finally {
                leitor.dispose();
            }
        } catch (IIOException e) {
            // Também é o caso de JPEG em CMYK, que o Java não decodifica.
            throw erro("Não foi possível ler a imagem. Salve-a como JPG ou PNG comum (cores RGB) e tente de novo.");
        } catch (IOException | RuntimeException e) {
            if (e instanceof PromocaoInvalida invalida) {
                throw invalida;
            }
            throw erro("Não foi possível ler a imagem. O arquivo parece estar corrompido.");
        }
    }

    // ------------------------------------------------------------------ reduzir e gravar

    /** Reduz para caber em 1000 x 1000 (sem ampliar) e põe sobre fundo branco: JPEG não tem transparência. */
    private static BufferedImage reduzirSobreBranco(BufferedImage original) {
        int largura = original.getWidth();
        int altura = original.getHeight();
        double escala = Math.min(1.0, Math.min((double) LARGURA_MAXIMA / largura, (double) ALTURA_MAXIMA / altura));
        int novaLargura = Math.max(1, (int) Math.round(largura * escala));
        int novaAltura = Math.max(1, (int) Math.round(altura * escala));

        BufferedImage saida = new BufferedImage(novaLargura, novaAltura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = saida.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, novaLargura, novaAltura);
            if (escala < 1.0) {
                Image reduzida = original.getScaledInstance(novaLargura, novaAltura, Image.SCALE_AREA_AVERAGING);
                g.drawImage(reduzida, 0, 0, null);
            } else {
                g.drawImage(original, 0, 0, null);
            }
        } finally {
            g.dispose();
        }
        return saida;
    }

    /** Grava como JPEG, baixando a qualidade só se o arquivo ainda ficar grande. */
    private static ImagemProcessada codificar(BufferedImage imagem) {
        for (int qualidade : QUALIDADES) {
            byte[] jpeg = gravarJpeg(imagem, qualidade / 100f);
            if (jpeg.length <= TAMANHO_MAXIMO_SAIDA) {
                return new ImagemProcessada(jpeg, imagem.getWidth(), imagem.getHeight());
            }
        }
        throw erro("A imagem é detalhada demais para ficar leve. Reduza as dimensões e tente de novo.");
    }

    private static byte[] gravarJpeg(BufferedImage imagem, float qualidade) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream();
             ImageOutputStream destino = ImageIO.createImageOutputStream(saida)) {
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(qualidade);
            escritor.setOutput(destino);
            escritor.write(null, new IIOImage(imagem, null, null), parametros);
            destino.flush();
            return saida.toByteArray();
        } catch (IOException e) {
            throw erro("Não foi possível preparar a imagem.");
        } finally {
            escritor.dispose();
        }
    }

    private static PromocaoInvalida erro(String mensagem) {
        return new PromocaoInvalida("imagem", mensagem);
    }
}
