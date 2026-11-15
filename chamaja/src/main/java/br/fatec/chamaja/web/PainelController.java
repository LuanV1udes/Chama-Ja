package br.fatec.chamaja.web;

import br.fatec.chamaja.model.EstadoFila;
import br.fatec.chamaja.model.Estabelecimento;
import br.fatec.chamaja.repository.OperadorRepository;
import br.fatec.chamaja.service.FilaException;
import br.fatec.chamaja.service.FilaService;
import br.fatec.chamaja.service.SseHub;
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

/** Painel do operador (RF09 a RF13 e RF16). Tudo aqui exige login. */
@Controller
@RequestMapping("/painel")
public class PainelController {

    private final OperadorRepository operadores;
    private final FilaService fila;
    private final SseHub sse;
    private final UrlsPublicas urls;

    public PainelController(OperadorRepository operadores,
                            FilaService fila,
                            SseHub sse,
                            UrlsPublicas urls) {
        this.operadores = operadores;
        this.fila = fila;
        this.sse = sse;
        this.urls = urls;
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

    /** RF16: link da tela de entrada (o tablet da porta mostra o QR rotativo). */
    @GetMapping("/tela")
    public String tela(Authentication auth, Model model) {
        Estabelecimento est = estabelecimento(auth);
        model.addAttribute("est", est);
        model.addAttribute("urlTela", urls.urlDaTela(est));
        return "operador/tela";
    }

    /** Gera outro link para a tela de entrada, invalidando o anterior (se o link vazou ou o tablet sumiu). */
    @PostMapping("/tela/renovar")
    public String renovarTela(Authentication auth, RedirectAttributes flash) {
        return executar(flash, () -> {
            fila.renovarCodigoTela(estabelecimento(auth).getId());
            flash.addFlashAttribute("ok", "Novo link gerado. O link antigo deixou de funcionar: abra o novo no tablet da entrada.");
        }, "redirect:/painel/tela");
    }

    // ------------------------------------------------------------------ internos

    private Estabelecimento estabelecimento(Authentication auth) {
        return operadores.findByUsuario(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Operador autenticado não existe mais."))
                .getEstabelecimento();
    }

    private String executar(RedirectAttributes flash, Runnable acao) {
        return executar(flash, acao, "redirect:/painel");
    }

    private String executar(RedirectAttributes flash, Runnable acao, String destino) {
        try {
            acao.run();
        } catch (FilaException e) {
            flash.addFlashAttribute("erro", e.getMessage());
        }
        return destino;
    }
}
