package br.fatec.chamaja.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Tempo real por Server-Sent Events (RNF03). Cada tela aberta assina o estabelecimento dela;
 * quando a fila muda (depois do commit), todas recebem o evento "fila" e se atualizam.
 */
@Component
public class SseHub {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, Set<SseEmitter>> assinantes = new ConcurrentHashMap<>();

    public SseEmitter assinar(Long estabelecimentoId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Set<SseEmitter> grupo = assinantes.computeIfAbsent(estabelecimentoId, id -> new CopyOnWriteArraySet<>());
        grupo.add(emitter);

        Runnable remover = () -> grupo.remove(emitter);
        emitter.onCompletion(remover);
        emitter.onTimeout(remover);
        emitter.onError(e -> remover.run());

        enviar(grupo, emitter, "pronto");
        return emitter;
    }

    /** fallbackExecution: também dispara se o evento for publicado fora de uma transação. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoAtualizar(FilaAtualizada evento) {
        Set<SseEmitter> grupo = assinantes.get(evento.estabelecimentoId());
        if (grupo != null) {
            grupo.forEach(emitter -> enviar(grupo, emitter, "fila"));
        }
    }

    /** Mantém a conexão viva atrás de proxies que derrubam conexões ociosas. */
    @Scheduled(fixedDelay = 25_000)
    public void batimento() {
        assinantes.values().forEach(grupo -> grupo.forEach(emitter -> enviar(grupo, emitter, "ping")));
    }

    private void enviar(Set<SseEmitter> grupo, SseEmitter emitter, String nome) {
        try {
            emitter.send(SseEmitter.event().name(nome).data("1"));
        } catch (IOException | IllegalStateException e) {
            grupo.remove(emitter);
        }
    }
}
