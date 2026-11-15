package br.fatec.chamaja.web;

import br.fatec.chamaja.model.Estabelecimento;
import br.fatec.chamaja.model.Senha;
import br.fatec.chamaja.service.EstadoSenha;
import br.fatec.chamaja.service.FilaException;
import br.fatec.chamaja.service.FilaService;
import br.fatec.chamaja.service.QrAcessoService;
import br.fatec.chamaja.service.SseHub;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;

/**
 * Lado do cliente (RF01 a RF08): sem cadastro, acesso pelo QR code.
 *
 * <p>Para pegar senha o cliente precisa ter escaneado o QR rotativo da tela de entrada (o código
 * muda a cada minuto). Escanear dá ao aparelho uma permissão curta e de uso único. A senha fica
 * ligada ao aparelho por um cookie com o token (RNF07), e um aparelho só segura uma senha ativa
 * por estabelecimento.
 */
@Controller
public class ClienteController {

    private static final String PREFIXO_COOKIE = "chamaja_";
    private static final String PREFIXO_PERMISSAO = "chamaja_ok_";

    private final FilaService fila;
    private final SseHub sse;
    private final QrAcessoService acesso;

    public ClienteController(FilaService fila, SseHub sse, QrAcessoService acesso) {
        this.fila = fila;
        this.sse = sse;
        this.acesso = acesso;
    }

    /**
     * RF01: página aberta pelo QR code (/q/{codigo}?t={código do momento}).
     * Se o aparelho já tem senha ativa, vai direto para ela.
     */
    @GetMapping("/q/{codigo}")
    public String entrada(@PathVariable String codigo,
                          @RequestParam(name = "t", required = false) String token,
                          HttpServletRequest request,
                          HttpServletResponse response,
                          Model model) {
        Estabelecimento est = estabelecimento(codigo);
        String existente = senhaAtivaDoAparelho(request, est);
        if (existente != null) {
            return "redirect:/senha/" + existente;
        }

        if (token != null) {
            if (acesso.tokenValido(codigo, token)) {
                // Código autêntico e recente: libera este aparelho e tira o código da barra de endereço.
                gravarPermissao(request, response, codigo);
                return "redirect:/q/" + codigo;
            }
            model.addAttribute("erro", "Este QR code expirou. Escaneie de novo o QR code da tela de entrada.");
        }

        model.addAttribute("est", est);
        model.addAttribute("podeRetirar", acesso.permissaoValida(codigo, lerCookie(request, PREFIXO_PERMISSAO + codigo)));
        return "cliente/entrada";
    }

    /** RF02: retira a senha informando só o nome. Exige a permissão dada ao escanear o QR. */
    @PostMapping("/q/{codigo}/senha")
    public String retirar(@PathVariable String codigo,
                          @RequestParam(defaultValue = "") String nome,
                          HttpServletRequest request,
                          HttpServletResponse response,
                          RedirectAttributes flash) {
        Estabelecimento est = estabelecimento(codigo);
        String existente = senhaAtivaDoAparelho(request, est);
        if (existente != null) {
            return "redirect:/senha/" + existente;
        }

        String permissao = lerCookie(request, PREFIXO_PERMISSAO + codigo);
        if (!acesso.permissaoValida(codigo, permissao)) {
            flash.addFlashAttribute("erro", "Escaneie o QR code na entrada do estabelecimento para pegar a senha.");
            return "redirect:/q/" + codigo;
        }
        try {
            Senha senha = fila.retirar(codigo, nome);
            // Uso único: só consome depois de a senha ter sido emitida (um erro de nome não gasta o QR).
            acesso.consumirPermissao(codigo, permissao);
            apagarCookie(request, response, PREFIXO_PERMISSAO + codigo);
            gravarCookie(request, response, PREFIXO_COOKIE + codigo, senha.getToken(), Duration.ofHours(12));
            return "redirect:/senha/" + senha.getToken();
        } catch (FilaException e) {
            flash.addFlashAttribute("erro", e.getMessage());
            return "redirect:/q/" + codigo;
        }
    }

    /** Tela "Minha senha". Os dados dinâmicos chegam por /estado e pelo SSE. */
    @GetMapping("/senha/{token}")
    public String minhaSenha(@PathVariable String token, HttpServletResponse response, Model model) {
        return fila.buscarSenha(token)
                .map(senha -> {
                    model.addAttribute("senha", senha);
                    return "cliente/senha";
                })
                .orElseGet(() -> {
                    response.setStatus(HttpStatus.NOT_FOUND.value());
                    return "cliente/expirada";
                });
    }

    /** RF03 a RF08 em JSON, para a tela se redesenhar sem recarregar. */
    @GetMapping("/senha/{token}/estado")
    @ResponseBody
    public ResponseEntity<EstadoSenha> estado(@PathVariable String token) {
        return fila.estado(token)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** RNF03: o servidor avisa a tela quando a fila muda. */
    @GetMapping("/senha/{token}/eventos")
    @ResponseBody
    public SseEmitter eventos(@PathVariable String token) {
        Senha senha = fila.buscarSenha(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return sse.assinar(senha.getEstabelecimento().getId());
    }

    /** RF07: sai da fila antes de ser chamado. */
    @PostMapping("/senha/{token}/sair")
    public String sair(@PathVariable String token, RedirectAttributes flash) {
        String codigo = fila.buscarSenha(token)
                .map(s -> s.getEstabelecimento().getCodigoQr())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {
            fila.cancelar(token);
        } catch (FilaException e) {
            flash.addFlashAttribute("erro", e.getMessage());
            return "redirect:/senha/" + token;
        }
        return "redirect:/q/" + codigo;
    }

    // ------------------------------------------------------------------ internos

    private Estabelecimento estabelecimento(String codigo) {
        return fila.buscarPorCodigo(codigo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** Token da senha ativa guardada neste aparelho para o estabelecimento, ou null. */
    private String senhaAtivaDoAparelho(HttpServletRequest request, Estabelecimento est) {
        String valor = lerCookie(request, PREFIXO_COOKIE + est.getCodigoQr());
        return valor != null && fila.senhaAtiva(valor, est.getId()) ? valor : null;
    }

    private static String lerCookie(HttpServletRequest request, String nome) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (nome.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void gravarPermissao(HttpServletRequest request, HttpServletResponse response, String codigo) {
        gravarCookie(request, response, PREFIXO_PERMISSAO + codigo, acesso.emitirPermissao(codigo),
                Duration.ofSeconds(acesso.getPermissaoSegundos()));
    }

    private void gravarCookie(HttpServletRequest request, HttpServletResponse response,
                              String nome, String valor, Duration validade) {
        ResponseCookie cookie = ResponseCookie.from(nome, valor)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(validade)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void apagarCookie(HttpServletRequest request, HttpServletResponse response, String nome) {
        gravarCookie(request, response, nome, "", Duration.ZERO);
    }
}
