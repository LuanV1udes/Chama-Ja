package br.fatec.chamaja.service;

import br.fatec.chamaja.model.Estabelecimento;
import br.fatec.chamaja.model.InscricaoPush;
import br.fatec.chamaja.model.Senha;
import br.fatec.chamaja.model.StatusSenha;
import br.fatec.chamaja.repository.EstabelecimentoRepository;
import br.fatec.chamaja.repository.InscricaoPushRepository;
import br.fatec.chamaja.repository.SenhaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Avisos no celular (Web Push): o cliente é avisado mesmo com a tela bloqueada.
 *
 * <p>Três momentos: ao ativar os avisos (confirmação), quando faltam até 3 números e quando a senha é
 * chamada ou repetida. Os envios saem numa thread própria, depois do commit, para uma falha do serviço
 * de push nunca atrapalhar o painel do operador. Sem chaves VAPID configuradas, o recurso fica desligado
 * e a tela do cliente nem oferece a opção.
 */
@Service
public class AvisosPushService {

    private static final Logger log = LoggerFactory.getLogger(AvisosPushService.class);

    private final boolean ativo;
    private final String chavePublica;
    private final PushSender sender;
    private final SenhaRepository senhas;
    private final EstabelecimentoRepository estabelecimentos;
    private final InscricaoPushRepository inscricoes;
    private final TransactionTemplate transacao;
    private final ObjectMapper json;

    private Executor executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "avisos-push");
        t.setDaemon(true);
        return t;
    });

    public AvisosPushService(@Value("${chamaja.push.publica:}") String chavePublica,
                             @Value("${chamaja.push.privada:}") String chavePrivada,
                             PushSender sender,
                             SenhaRepository senhas,
                             EstabelecimentoRepository estabelecimentos,
                             InscricaoPushRepository inscricoes,
                             PlatformTransactionManager gerenteDeTransacao,
                             ObjectMapper json) {
        this.ativo = !chavePublica.isBlank() && !chavePrivada.isBlank();
        this.chavePublica = chavePublica;
        this.sender = sender;
        this.senhas = senhas;
        this.estabelecimentos = estabelecimentos;
        this.inscricoes = inscricoes;
        // REQUIRES_NEW: estes métodos rodam depois do commit (AFTER_COMMIT). Uma transação "normal" aberta
        // ali participaria da transação anterior, já encerrada, e o que ela gravasse nunca seria confirmado.
        this.transacao = new TransactionTemplate(gerenteDeTransacao);
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.json = json;
        if (!ativo) {
            log.info("Web Push desligado: defina VAPID_PUBLIC e VAPID_PRIVATE para avisar o cliente com a tela bloqueada.");
        }
    }

    public boolean ativo() {
        return ativo;
    }

    /** Chave pública VAPID que o navegador usa para se inscrever; null se o recurso estiver desligado. */
    public String chavePublica() {
        return ativo ? chavePublica : null;
    }

    /** Para os testes: executa os envios na própria thread, em vez de esperar a thread de fundo. */
    public void executarNaMesmaThread() {
        this.executor = Runnable::run;
    }

    // ------------------------------------------------------------------ inscrição

    /** Resultado de uma inscrição; {@code nova} é false quando o mesmo aparelho já estava inscrito nesta senha. */
    public record Inscrita(Long inscricaoId, String token, String numero, String endpoint,
                           String p256dh, String auth, boolean nova) {
    }

    /** Liga o aparelho do cliente à senha dele. Uma senha tem uma inscrição; reinscrever substitui a antiga. */
    @Transactional
    public Inscrita inscrever(String token, String endpoint, String p256dh, String auth) {
        Senha senha = senhas.findByToken(token)
                .orElseThrow(() -> new FilaException("Senha não encontrada."));
        if (!senha.getStatus().isAtiva()) {
            throw new FilaException("Esta senha já foi encerrada.");
        }
        if (!EnderecoPush.valido(endpoint)) {
            throw new FilaException("Endereço de notificação não aceito.");
        }
        if (!EnderecoPush.chavesValidas(p256dh, auth)) {
            throw new FilaException("Chaves de notificação inválidas.");
        }

        InscricaoPush daSenha = inscricoes.findBySenhaId(senha.getId()).orElse(null);
        if (daSenha != null && daSenha.getEndpoint().equals(endpoint)) {
            daSenha.atualizarChaves(p256dh, auth);
            return new Inscrita(daSenha.getId(), token, senha.getNumeroFormatado(), endpoint, p256dh, auth, false);
        }
        if (daSenha != null) {
            inscricoes.delete(daSenha);
        }
        inscricoes.apagarPorEndpoint(endpoint);
        // O apagarPorEndpoint limpa o contexto: busca a senha de novo para ligar a nova inscrição.
        Senha atual = senhas.findByToken(token).orElseThrow();
        InscricaoPush nova = inscricoes.save(new InscricaoPush(atual, endpoint, p256dh, auth));
        return new Inscrita(nova.getId(), token, atual.getNumeroFormatado(), endpoint, p256dh, auth, true);
    }

    /** Confirma ao cliente que os avisos funcionam (a primeira notificação já prova o caminho completo). */
    public void enviarConfirmacao(Inscrita inscrita) {
        if (!ativo) {
            return;
        }
        executar("confirmação", () -> enviar(new Envio(inscrita.inscricaoId(), inscrita.endpoint(),
                inscrita.p256dh(), inscrita.auth(),
                payload("Avisos ativados", "Vamos te avisar quando a sua vez estiver chegando.",
                        urlDaSenha(inscrita.token()), "confirmacao", true),
                false)));
    }

    // ------------------------------------------------------------------ gatilhos

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoChamar(SenhaChamada evento) {
        if (ativo) {
            executar("chamada", () -> notificarChamada(evento.senhaId(), evento.repeticao()));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoAvancar(FilaAvancou evento) {
        if (ativo) {
            executar("proximidade", () -> avisarProximidade(evento.estabelecimentoId()));
        }
    }

    // ------------------------------------------------------------------ envios

    private record Envio(Long inscricaoId, String endpoint, String p256dh, String auth,
                         String payload, boolean urgente) {
    }

    /** "É a sua vez!" para a senha chamada (ou "estamos chamando" se o operador repetiu a chamada). */
    void notificarChamada(Long senhaId, boolean repeticao) {
        Senha senha = senhas.findById(senhaId).orElse(null);
        if (senha == null || senha.getStatus() != StatusSenha.CHAMADA) {
            return;
        }
        InscricaoPush inscricao = inscricoes.findBySenhaId(senhaId).orElse(null);
        if (inscricao == null) {
            return;
        }
        String titulo = repeticao ? "Estamos chamando a sua senha" : "É a sua vez!";
        String corpo = "Senha " + senha.getNumeroFormatado() + ": dirija-se ao atendimento.";
        enviar(new Envio(inscricao.getId(), inscricao.getEndpoint(), inscricao.getP256dh(), inscricao.getAuth(),
                payload(titulo, corpo, urlDaSenha(senha.getToken()), "chamada", false), true));
    }

    /**
     * "Sua vez está chegando": para cada senha aguardando a até 3 números da atual, e que ainda não foi
     * avisada, manda um push (uma vez só). A marca de "avisada" é gravada antes do envio.
     */
    void avisarProximidade(Long estabelecimentoId) {
        List<Envio> pendentes = transacao.execute(status -> {
            Estabelecimento est = estabelecimentos.findById(estabelecimentoId).orElse(null);
            if (est == null) {
                return List.<Envio>of();
            }
            int limite = est.getSenhaAtual() + FilaService.DISTANCIA_AVISO;
            List<Senha> perto = senhas
                    .findByEstabelecimentoIdAndStatusAndNumeroLessThanEqualAndPushProximidadeEnviadoFalseOrderByNumeroAsc(
                            estabelecimentoId, StatusSenha.AGUARDANDO, limite);
            if (perto.isEmpty()) {
                return List.<Envio>of();
            }
            Map<Long, InscricaoPush> porSenha = inscricoes
                    .findBySenhaIdIn(perto.stream().map(Senha::getId).toList()).stream()
                    .collect(Collectors.toMap(i -> i.getSenha().getId(), Function.identity()));
            List<Envio> lista = new ArrayList<>();
            for (Senha senha : perto) {
                InscricaoPush inscricao = porSenha.get(senha.getId());
                if (inscricao == null) {
                    continue; // sem push ativado: o aviso na tela cuida dele
                }
                senha.setPushProximidadeEnviado(true);
                String corpo = "Faltam poucos números para a sua senha " + senha.getNumeroFormatado()
                        + ". Volte para perto do atendimento.";
                lista.add(new Envio(inscricao.getId(), inscricao.getEndpoint(), inscricao.getP256dh(),
                        inscricao.getAuth(),
                        payload("Sua vez está chegando!", corpo, urlDaSenha(senha.getToken()), "proximidade", false),
                        false));
            }
            return lista;
        });
        if (pendentes != null) {
            pendentes.forEach(this::enviar);
        }
    }

    private void enviar(Envio envio) {
        PushSender.Resultado resultado = sender.enviar(
                envio.endpoint(), envio.p256dh(), envio.auth(), envio.payload(), envio.urgente());
        if (resultado == PushSender.Resultado.EXPIRADA && envio.inscricaoId() != null) {
            // O aparelho não aceita mais avisos (revogou ou desinstalou): esquece a inscrição.
            transacao.executeWithoutResult(status -> inscricoes.deleteById(envio.inscricaoId()));
        }
    }

    private void executar(String descricao, Runnable tarefa) {
        executor.execute(() -> {
            try {
                tarefa.run();
            } catch (RuntimeException e) {
                log.warn("Falha ao enviar o aviso de push ({}): {}", descricao, e.toString());
            }
        });
    }

    private static String urlDaSenha(String token) {
        return "/senha/" + token;
    }

    /** O que o service worker lê: título, texto, para onde levar o toque e se deve aparecer mesmo com a página aberta. */
    private String payload(String titulo, String corpo, String url, String tipo, boolean forcar) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("titulo", titulo);
        dados.put("corpo", corpo);
        dados.put("url", url);
        dados.put("tipo", tipo);
        dados.put("forcar", forcar);
        try {
            return json.writeValueAsString(dados);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
