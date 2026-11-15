package br.fatec.chamaja.web;

import br.fatec.chamaja.model.Promocao;
import br.fatec.chamaja.service.PromocaoService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;

import java.time.Duration;

/**
 * Banner da promoção para a tela do cliente. É público (a promoção é divulgação), mas só entrega o banner de
 * promoção ativa e dentro da validade: a de ontem ou a desativada some, mesmo com o endereço na mão.
 */
@Controller
public class BannerController {

    private final PromocaoService promocoes;

    public BannerController(PromocaoService promocoes) {
        this.promocoes = promocoes;
    }

    @GetMapping("/promocoes/{id}/imagem")
    @ResponseBody
    public ResponseEntity<byte[]> banner(@PathVariable Long id, WebRequest requisicao) {
        Promocao promocao = promocoes.publicaComImagem(id).orElse(null);
        if (promocao == null) {
            return ResponseEntity.notFound().build();
        }
        // A versão muda a cada troca de imagem: o navegador guarda o arquivo e só pergunta se mudou (304).
        String etag = "\"" + promocao.getImagemVersao() + "\"";
        if (requisicao.checkNotModified(etag)) {
            return null;
        }
        return promocoes.bytesDaImagem(id)
                .map(jpeg -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(12)).cachePublic())
                        .eTag(etag)
                        .header("X-Content-Type-Options", "nosniff")
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(jpeg))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
