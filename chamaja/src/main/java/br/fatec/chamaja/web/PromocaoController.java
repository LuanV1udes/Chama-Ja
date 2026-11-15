package br.fatec.chamaja.web;

import br.fatec.chamaja.model.Estabelecimento;
import br.fatec.chamaja.model.Promocao;
import br.fatec.chamaja.repository.OperadorRepository;
import br.fatec.chamaja.service.FilaException;
import br.fatec.chamaja.service.PromocaoInvalida;
import br.fatec.chamaja.service.PromocaoService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** RF14 e RF15: cadastrar, editar, ativar/desativar e excluir promoções (com ou sem banner pronto). */
@Controller
@RequestMapping("/painel/promocoes")
public class PromocaoController {

    private final OperadorRepository operadores;
    private final PromocaoService promocoes;

    public PromocaoController(OperadorRepository operadores, PromocaoService promocoes) {
        this.operadores = operadores;
        this.promocoes = promocoes;
    }

    @GetMapping
    public String listar(Authentication auth, Model model) {
        Estabelecimento est = estabelecimento(auth);
        model.addAttribute("est", est);
        model.addAttribute("promocoes", promocoes.listar(est.getId()));
        return "operador/promocoes";
    }

    @GetMapping("/nova")
    public String nova(Authentication auth, Model model) {
        model.addAttribute("est", estabelecimento(auth));
        model.addAttribute("form", new PromocaoForm());
        model.addAttribute("imagemAtual", false);
        return "operador/promocao-form";
    }

    @GetMapping("/{id}/editar")
    public String editar(Authentication auth, @PathVariable Long id, Model model) {
        Estabelecimento est = estabelecimento(auth);
        Promocao promocao = buscar(est, id);
        model.addAttribute("est", est);
        model.addAttribute("form", PromocaoForm.de(promocao));
        informarBanner(model, promocao);
        return "operador/promocao-form";
    }

    @PostMapping
    public String salvar(Authentication auth,
                         @Valid @ModelAttribute("form") PromocaoForm form,
                         BindingResult erros,
                         Model model,
                         RedirectAttributes flash) {
        Estabelecimento est = estabelecimento(auth);
        if (!erros.hasErrors()) {
            try {
                promocoes.salvar(est.getId(), form);
                flash.addFlashAttribute("ok", "Promoção salva.");
                return "redirect:/painel/promocoes";
            } catch (PromocaoInvalida e) {
                // Preço sem banner, imagem inválida...: a mensagem aparece junto do campo.
                erros.rejectValue(e.getCampo(), "invalido", e.getMessage());
            } catch (FilaException e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        }
        model.addAttribute("est", est);
        if (form.getId() != null) {
            informarBanner(model, buscar(est, form.getId()));
        } else {
            model.addAttribute("imagemAtual", false);
        }
        return "operador/promocao-form";
    }

    /** Prévia do banner no painel (mostra também as promoções desativadas). */
    @GetMapping("/{id}/imagem")
    @ResponseBody
    public ResponseEntity<byte[]> imagem(Authentication auth, @PathVariable Long id) {
        try {
            byte[] jpeg = promocoes.imagemDoOperador(estabelecimento(auth).getId(), id);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header("X-Content-Type-Options", "nosniff")
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(jpeg);
        } catch (FilaException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/ativa")
    public String alternar(Authentication auth, @PathVariable Long id, RedirectAttributes flash) {
        try {
            promocoes.alternarAtiva(estabelecimento(auth).getId(), id);
        } catch (FilaException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "redirect:/painel/promocoes";
    }

    @PostMapping("/{id}/excluir")
    public String excluir(Authentication auth, @PathVariable Long id, RedirectAttributes flash) {
        try {
            promocoes.excluir(estabelecimento(auth).getId(), id);
        } catch (FilaException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        flash.addFlashAttribute("ok", "Promoção excluída.");
        return "redirect:/painel/promocoes";
    }

    // (Arquivo acima de 3 MB: tratado em ErrosDeEnvio, porque o erro acontece antes de chegar aqui.)

    // ------------------------------------------------------------------ internos

    private static void informarBanner(Model model, Promocao promocao) {
        model.addAttribute("imagemAtual", promocao.temImagem());
        model.addAttribute("imagemVersao", promocao.getImagemVersao());
    }

    private Promocao buscar(Estabelecimento est, Long id) {
        try {
            return promocoes.buscar(est.getId(), id);
        } catch (FilaException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private Estabelecimento estabelecimento(Authentication auth) {
        return operadores.findByUsuario(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Operador autenticado não existe mais."))
                .getEstabelecimento();
    }
}
