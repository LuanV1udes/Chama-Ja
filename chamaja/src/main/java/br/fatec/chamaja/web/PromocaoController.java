package br.fatec.chamaja.web;

import br.fatec.chamaja.model.Estabelecimento;
import br.fatec.chamaja.repository.OperadorRepository;
import br.fatec.chamaja.service.FilaException;
import br.fatec.chamaja.service.PromocaoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** RF14 e RF15: cadastrar, editar, ativar/desativar e excluir promoções. */
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
        return "operador/promocao-form";
    }

    @GetMapping("/{id}/editar")
    public String editar(Authentication auth, @PathVariable Long id, Model model) {
        Estabelecimento est = estabelecimento(auth);
        model.addAttribute("est", est);
        model.addAttribute("form", PromocaoForm.de(buscar(est, id)));
        return "operador/promocao-form";
    }

    @PostMapping
    public String salvar(Authentication auth,
                         @Valid @ModelAttribute("form") PromocaoForm form,
                         BindingResult erros,
                         Model model,
                         RedirectAttributes flash) {
        Estabelecimento est = estabelecimento(auth);
        if (erros.hasErrors()) {
            model.addAttribute("est", est);
            return "operador/promocao-form";
        }
        try {
            promocoes.salvar(est.getId(), form);
        } catch (FilaException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        flash.addFlashAttribute("ok", "Promoção salva.");
        return "redirect:/painel/promocoes";
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

    private br.fatec.chamaja.model.Promocao buscar(Estabelecimento est, Long id) {
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
