package br.fatec.chamaja.web;

import br.fatec.chamaja.model.EstadoFila;
import br.fatec.chamaja.model.Estabelecimento;
import br.fatec.chamaja.repository.OperadorRepository;
import br.fatec.chamaja.service.FilaException;
import br.fatec.chamaja.service.FilaService;
import br.fatec.chamaja.service.QrCodeService;
import br.fatec.chamaja.service.SseHub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Painel do operador (RF09 a RF13 e RF16). Tudo aqui exige login. */
@Controller
@RequestMapping("/painel")
public class PainelController {

    private final OperadorRepository operadores;
    private final FilaService fila;
    private final QrCodeService qrCode;
    private final SseHub sse;
    private final String baseUrl;

    public PainelController(OperadorRepository operadores,
                            FilaService fila,
                            QrCodeService qrCode,
                            SseHub sse,
                            @Value("${chamaja.base-url:}") String baseUrl) {
        this.operadores = operadores;
        this.fila = fila;
        this.qrCode = qrCode;
        this.sse = sse;
        this.baseUrl = baseUrl;
    }

    /** RF12: fila completa com o status de cada senha. */
    @GetMapping
    public String painel(Authentication auth, Model model) {
        Estabelecimento est = estabelecimento(auth);
        model.addAttribute("est", est);
        model.addAttribute("atual", fila.chamadaAtual(est.getId()).orElse(null));
        model.addAttribute("aguardando", fila.contarAguardando(est.getId()));
        model.addAttribute("senhas", fila.listar(est.getId()));
        model.addAttribute("estados", EstadoFila.values());
        return "operador/painel";
    }

    @PostMapping("/chamar")
    public String chamar(Authentication auth, RedirectAttributes flash) {
        return executar(flash, () -> fila.chamarProxima(estabelecimento(auth).getId()));
    }

    @PostMapping("/repetir")
    public String repetir(Authentication auth, RedirectAttributes flash) {
        return executar(flash, () -> fila.repetir(estabelecimento(auth).getId()));
    }

    @PostMapping("/pular")
    public String pular(Authentication auth, RedirectAttributes flash) {
        return executar(flash, () -> fila.pular(estabelecimento(auth).getId()));
    }

    @PostMapping("/finalizar")
    public String finalizar(Authentication auth, RedirectAttributes flash) {
        return executar(flash, () -> fila.finalizar(estabelecimento(auth).getId()));
    }

    @PostMapping("/estado")
    public String estado(Authentication auth, @RequestParam EstadoFila estado, RedirectAttributes flash) {
        return executar(flash, () -> fila.definirEstado(estabelecimento(auth).getId(), estado));
    }

    @PostMapping("/zerar")
    public String zerar(Authentication auth, RedirectAttributes flash) {
        return executar(flash, () -> fila.zerar(estabelecimento(auth).getId()));
    }

    /** O painel recarrega sozinho quando a fila muda (por exemplo, um cliente sai). */
    @GetMapping("/eventos")
    @ResponseBody
    public SseEmitter eventos(Authentication auth) {
        return sse.assinar(estabelecimento(auth).getId());
    }

    /** RF16: página para imprimir o QR code. */
    @GetMapping("/qr")
    public String qr(Authentication auth, Model model) {
        Estabelecimento est = estabelecimento(auth);
        model.addAttribute("est", est);
        model.addAttribute("url", urlDoCliente(est));
        return "operador/qr";
    }

    @GetMapping(value = "/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> qrPng(Authentication auth) {
        byte[] png = qrCode.png(urlDoCliente(estabelecimento(auth)), 600);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    // ------------------------------------------------------------------ internos

    private Estabelecimento estabelecimento(Authentication auth) {
        return operadores.findByUsuario(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Operador autenticado não existe mais."))
                .getEstabelecimento();
    }

    private String urlDoCliente(Estabelecimento est) {
        String base = baseUrl.isBlank()
                ? ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                : baseUrl.replaceAll("/+$", "");
        return base + "/q/" + est.getCodigoQr();
    }

    private String executar(RedirectAttributes flash, Runnable acao) {
        try {
            acao.run();
        } catch (FilaException e) {
            flash.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/painel";
    }
}
