package br.fatec.chamaja.web;

import br.fatec.chamaja.model.Estabelecimento;
import br.fatec.chamaja.service.FilaService;
import br.fatec.chamaja.service.QrAcessoService;
import br.fatec.chamaja.service.QrCodeService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tela de entrada: o tablet na porta do estabelecimento abre /tela/{segredo} e mostra um QR code
 * que muda a cada minuto. O segredo na URL substitui o login (o tablet fica numa área pública e
 * não deve ter acesso ao painel). Se o link vazar, o operador gera outro no painel.
 */
@Controller
public class TelaController {

    private static final String SEM_CACHE = "no-store";

    private final FilaService fila;
    private final QrAcessoService acesso;
    private final QrCodeService qrCode;
    private final UrlsPublicas urls;

    public TelaController(FilaService fila, QrAcessoService acesso, QrCodeService qrCode, UrlsPublicas urls) {
        this.fila = fila;
        this.acesso = acesso;
        this.qrCode = qrCode;
        this.urls = urls;
    }

    @GetMapping("/tela/{segredo}")
    public String tela(@PathVariable String segredo, HttpServletResponse response, Model model) {
        Estabelecimento est = estabelecimento(segredo);
        proteger(response);
        model.addAttribute("est", est);
        // Atualiza o QR bem antes de o código vencer.
        model.addAttribute("intervalo", Math.max(5, acesso.getJanelaSegundos() / 3));
        return "tela/entrada";
    }

    /** O QR code do momento (a página o busca de novo de tempos em tempos). */
    @GetMapping(value = "/tela/{segredo}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> qr(@PathVariable String segredo) {
        Estabelecimento est = estabelecimento(segredo);
        byte[] png = qrCode.png(urls.urlDoCliente(est, acesso.tokenAtual(est.getCodigoQr())), 600);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex")
                .header("Referrer-Policy", "no-referrer")
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    private Estabelecimento estabelecimento(String segredo) {
        return fila.buscarPorCodigoTela(segredo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** O segredo está na URL: não deixa a página ser guardada, indexada nem repassada por Referer. */
    private void proteger(HttpServletResponse response) {
        response.setHeader("Cache-Control", SEM_CACHE);
        response.setHeader("X-Robots-Tag", "noindex");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
}
