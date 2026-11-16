package br.fatec.chamaja.web;

import br.fatec.chamaja.service.AvisosPushService;
import br.fatec.chamaja.service.FilaException;
import br.fatec.chamaja.service.FilaService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Web Push do cliente. A senha (token na URL, impossível de adivinhar) é a credencial: quem tem o token
 * é o dono daquela senha, então não há login. O envio fica protegido por CSRF e pelo limite por IP.
 */
@RestController
public class PushController {

    /** O que o navegador manda ao se inscrever (PushSubscription.toJSON()). */
    public record InscricaoRequest(String endpoint, Chaves keys) {
        public record Chaves(String p256dh, String auth) {
        }
    }

    private final AvisosPushService push;
    private final FilaService fila;

    public PushController(AvisosPushService push, FilaService fila) {
        this.push = push;
        this.fila = fila;
    }

    @PostMapping("/senha/{token}/push")
    public ResponseEntity<Void> inscrever(@PathVariable String token, @RequestBody InscricaoRequest pedido) {
        if (!push.ativo() || fila.buscarSenha(token).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (pedido == null || pedido.keys() == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            AvisosPushService.Inscrita inscrita = push.inscrever(
                    token, pedido.endpoint(), pedido.keys().p256dh(), pedido.keys().auth());
            if (inscrita.nova()) {
                push.enviarConfirmacao(inscrita);
            }
            return ResponseEntity.noContent().build();
        } catch (FilaException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Manifesto do aplicativo para esta senha. No iPhone, o Web Push só funciona depois de "Adicionar à
     * Tela de Início", e o ícone instalado abre no endereço da senha (start_url): sem isso o cliente cairia
     * na página inicial, sem a senha dele (o iPhone não compartilha cookies entre o Safari e o ícone).
     */
    @GetMapping(value = "/senha/{token}/manifest.webmanifest", produces = "application/manifest+json")
    public ResponseEntity<Map<String, Object>> manifesto(@PathVariable String token) {
        if (fila.buscarSenha(token).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> manifesto = new LinkedHashMap<>();
        manifesto.put("name", "Chama Já");
        manifesto.put("short_name", "Chama Já");
        manifesto.put("description", "Sua vez, no seu tempo.");
        manifesto.put("start_url", "/senha/" + token);
        manifesto.put("scope", "/");
        manifesto.put("display", "standalone");
        manifesto.put("background_color", "#fff8f1");
        manifesto.put("theme_color", "#d9480f");
        manifesto.put("lang", "pt-BR");
        manifesto.put("icons", List.of(
                Map.of("src", "/img/icone-192.png", "sizes", "192x192", "type", "image/png"),
                Map.of("src", "/img/icone-512.png", "sizes", "512x512", "type", "image/png")));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("application/manifest+json"))
                .body(manifesto);
    }
}
