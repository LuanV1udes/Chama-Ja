package br.fatec.chamaja.web;

import br.fatec.chamaja.model.Estabelecimento;
import br.fatec.chamaja.model.Senha;
import br.fatec.chamaja.service.EstadoSenha;
import br.fatec.chamaja.service.FilaException;
import br.fatec.chamaja.service.FilaService;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * Lado do cliente (RF01 a RF08): sem cadastro, acesso pelo QR code.
 * A senha fica ligada ao aparelho por um cookie com o token (RNF07), e um aparelho
 * só segura uma senha ativa por estabelecimento.
 */
@Controller
public class ClienteController {

    private static final String PREFIXO_COOKIE = "chamaja_";

    private final FilaService fila;
    private final SseHub sse;

    public ClienteController(FilaService fila, SseHub sse) {
        this.fila = fila;
        this.sse = sse;
    }

    /** RF01: página aberta pelo QR code. Se o aparelho já tem senha ativa, vai direto para ela. */
    @GetMapping("/q/{codigo}")
    public String entrada(@PathVariable String codigo, HttpServletRequest request, Model model) {
        Estabelecimento est = estabelecimento(codigo);
        String existente = senhaAtivaDoAparelho(request, est);
        if (existente != null) {
            return "redirect:/senha/" + existente;
        }
        model.addAttribute("est", est);
        return "cliente/entrada";
    }

    /** RF02: retira a senha informando só o nome. */
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
        try {
            Senha senha = fila.retirar(codigo, nome);
            gravarCookie(request, response, codigo, senha.getToken());
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
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String nome = PREFIXO_COOKIE + est.getCodigoQr();
        for (Cookie cookie : cookies) {
            if (nome.equals(cookie.getName()) && fila.senhaAtiva(cookie.getValue(), est.getId())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void gravarCookie(HttpServletRequest request, HttpServletResponse response, String codigo, String token) {
        ResponseCookie cookie = ResponseCookie.from(PREFIXO_COOKIE + codigo, token)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofHours(12))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
