package br.fatec.chamaja.service;

import br.fatec.chamaja.model.EstadoFila;
import br.fatec.chamaja.model.Estabelecimento;
import br.fatec.chamaja.model.Promocao;
import br.fatec.chamaja.model.Senha;
import br.fatec.chamaja.model.StatusSenha;
import br.fatec.chamaja.repository.EstabelecimentoRepository;
import br.fatec.chamaja.repository.InscricaoPushRepository;
import br.fatec.chamaja.repository.PromocaoRepository;
import br.fatec.chamaja.repository.SenhaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class FilaService {

    /** Aviso de proximidade (RF05): dispara quando faltam até 3 números para a vez do cliente. */
    public static final int DISTANCIA_AVISO = 3;

    private static final int TAMANHO_MAX_NOME = 60;
    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EstabelecimentoRepository estabelecimentos;
    private final SenhaRepository senhas;
    private final PromocaoRepository promocoes;
    private final InscricaoPushRepository inscricoes;
    private final ApplicationEventPublisher eventos;

    public FilaService(EstabelecimentoRepository estabelecimentos,
                       SenhaRepository senhas,
                       PromocaoRepository promocoes,
                       InscricaoPushRepository inscricoes,
                       ApplicationEventPublisher eventos) {
        this.estabelecimentos = estabelecimentos;
        this.senhas = senhas;
        this.promocoes = promocoes;
        this.inscricoes = inscricoes;
        this.eventos = eventos;
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public Optional<Estabelecimento> buscarPorCodigo(String codigo) {
        return estabelecimentos.findByCodigoQr(codigo);
    }

    @Transactional(readOnly = true)
    public Optional<Senha> buscarSenha(String token) {
        return senhas.findByToken(token);
    }

    /** O token pertence a uma senha ainda ativa (aguardando ou chamada) deste estabelecimento? */
    @Transactional(readOnly = true)
    public boolean senhaAtiva(String token, Long estabelecimentoId) {
        return senhas.findByToken(token)
                .filter(s -> s.getEstabelecimento().getId().equals(estabelecimentoId))
                .filter(s -> s.getStatus().isAtiva())
                .isPresent();
    }

    @Transactional(readOnly = true)
    public List<Senha> listar(Long estabelecimentoId) {
        return senhas.findByEstabelecimentoIdOrderByNumeroAsc(estabelecimentoId);
    }

    @Transactional(readOnly = true)
    public Optional<Senha> chamadaAtual(Long estabelecimentoId) {
        return senhas.findFirstByEstabelecimentoIdAndStatusOrderByNumeroDesc(estabelecimentoId, StatusSenha.CHAMADA);
    }

    @Transactional(readOnly = true)
    public long contarAguardando(Long estabelecimentoId) {
        return senhas.countByEstabelecimentoIdAndStatus(estabelecimentoId, StatusSenha.AGUARDANDO);
    }

    // ------------------------------------------------------------------ cliente

    /** RF02: retira uma senha. Só funciona com a fila aberta. */
    @Transactional
    public Senha retirar(String codigo, String nomeInformado) {
        Estabelecimento est = estabelecimentos.findByCodigoQrForUpdate(codigo)
                .orElseThrow(() -> new FilaException("Estabelecimento não encontrado."));

        if (est.getEstadoFila() != EstadoFila.ABERTA) {
            throw new FilaException("A fila não está aceitando novas senhas no momento.");
        }
        String nome = nomeInformado == null ? "" : nomeInformado.trim();
        if (nome.isEmpty()) {
            throw new FilaException("Informe seu nome para retirar a senha.");
        }
        if (nome.length() > TAMANHO_MAX_NOME) {
            nome = nome.substring(0, TAMANHO_MAX_NOME);
        }

        Senha senha = senhas.save(new Senha(est, est.proximoNumero(), nome));
        avisarTelas(est.getId());
        return senha;
    }

    /** RF07: o cliente sai da fila antes de ser chamado. */
    @Transactional
    public void cancelar(String token) {
        Senha senha = senhas.findByToken(token)
                .orElseThrow(() -> new FilaException("Senha não encontrada."));
        if (senha.getStatus() != StatusSenha.AGUARDANDO) {
            throw new FilaException("Só é possível sair da fila antes de ser chamado.");
        }
        encerrar(senha, StatusSenha.CANCELADA);
        avisarTelas(senha.getEstabelecimento().getId());
    }

    /**
     * Estado da senha para a tela do cliente: posição, aviso de proximidade (RF05) e promoções.
     * Marca o aviso de proximidade como entregue na primeira consulta em que ele vale.
     */
    @Transactional
    public Optional<EstadoSenha> estado(String token) {
        return senhas.findByToken(token).map(this::montarEstado);
    }

    private EstadoSenha montarEstado(Senha senha) {
        Estabelecimento est = senha.getEstabelecimento();
        int posicao = 0;
        int aFrente = 0;
        boolean proximo = false;
        boolean novoAviso = false;

        if (senha.getStatus() == StatusSenha.AGUARDANDO) {
            aFrente = (int) senhas.countByEstabelecimentoIdAndStatusAndNumeroLessThan(
                    est.getId(), StatusSenha.AGUARDANDO, senha.getNumero());
            posicao = aFrente + 1;

            proximo = senha.getNumero() - est.getSenhaAtual() <= DISTANCIA_AVISO;
            if (proximo && !senha.isAvisoProximidadeEnviado()) {
                senha.setAvisoProximidadeEnviado(true);
                novoAviso = true;
            }
        }

        Instant chamadaEm = senha.getChamadaEm();
        return new EstadoSenha(
                senha.getNumeroFormatado(),
                senha.getNomeCliente(),
                senha.getStatus().name(),
                posicao,
                aFrente,
                est.getSenhaAtual() == 0 ? "---" : String.format("%03d", est.getSenhaAtual()),
                proximo,
                novoAviso,
                chamadaEm == null ? 0L : chamadaEm.toEpochMilli(),
                est.getEstadoFila().name(),
                promocoesAtivas(est.getId()));
    }

    private List<PromocaoView> promocoesAtivas(Long estabelecimentoId) {
        NumberFormat moeda = NumberFormat.getCurrencyInstance(PT_BR);
        return promocoes
                .findByEstabelecimentoIdAndAtivaTrueAndValidadeGreaterThanEqualOrderByTituloAsc(
                        estabelecimentoId, LocalDate.now())
                .stream()
                .map((Promocao p) -> new PromocaoView(
                        p.getTitulo(),
                        p.getDescricao() == null ? "" : p.getDescricao(),
                        moeda.format(p.getPreco()),
                        p.getValidade().format(DATA)))
                .toList();
    }

    // ------------------------------------------------------------------ operador

    /** RF10: chama a próxima senha. A senha chamada anteriormente é finalizada automaticamente. */
    @Transactional
    public Senha chamarProxima(Long estabelecimentoId) {
        Estabelecimento est = travar(estabelecimentoId);
        if (est.getEstadoFila() == EstadoFila.FECHADA) {
            throw new FilaException("A fila está fechada. Abra ou pause a fila para chamar senhas.");
        }
        Senha proxima = senhas
                .findFirstByEstabelecimentoIdAndStatusOrderByNumeroAsc(est.getId(), StatusSenha.AGUARDANDO)
                .orElseThrow(() -> new FilaException("Não há senhas aguardando."));

        senhas.findByEstabelecimentoIdAndStatus(est.getId(), StatusSenha.CHAMADA)
                .forEach(anterior -> encerrar(anterior, StatusSenha.FINALIZADA));

        proxima.setStatus(StatusSenha.CHAMADA);
        proxima.setChamadaEm(Instant.now());
        est.setSenhaAtual(proxima.getNumero());
        avisarTelas(est.getId());
        // Avisos de push (depois do commit): "é a sua vez" para esta senha e "está chegando" para as próximas.
        eventos.publishEvent(new SenhaChamada(proxima.getId(), false));
        eventos.publishEvent(new FilaAvancou(est.getId()));
        return proxima;
    }

    /** RF11: chama de novo a senha atual (o cliente é avisado outra vez). */
    @Transactional
    public Senha repetir(Long estabelecimentoId) {
        Senha atual = exigirChamadaAtual(estabelecimentoId);
        atual.setChamadaEm(Instant.now());
        avisarTelas(estabelecimentoId);
        eventos.publishEvent(new SenhaChamada(atual.getId(), true));
        return atual;
    }

    /** RF11: pula a senha atual. Ela não volta sozinha para a fila. */
    @Transactional
    public Senha pular(Long estabelecimentoId) {
        Senha atual = exigirChamadaAtual(estabelecimentoId);
        encerrar(atual, StatusSenha.PULADA);
        avisarTelas(estabelecimentoId);
        return atual;
    }

    /** RF11: finaliza o atendimento da senha atual. */
    @Transactional
    public Senha finalizar(Long estabelecimentoId) {
        Senha atual = exigirChamadaAtual(estabelecimentoId);
        encerrar(atual, StatusSenha.FINALIZADA);
        avisarTelas(estabelecimentoId);
        return atual;
    }

    /** RF13: abre, pausa ou fecha a fila. */
    @Transactional
    public void definirEstado(Long estabelecimentoId, EstadoFila estado) {
        travar(estabelecimentoId).setEstadoFila(estado);
        avisarTelas(estabelecimentoId);
    }

    /** RF13: zera a fila do dia. Apaga as senhas (e os nomes dos clientes) e reinicia a numeração. */
    @Transactional
    public void zerar(Long estabelecimentoId) {
        Estabelecimento est = travar(estabelecimentoId);
        // Os contadores são zerados ANTES do delete em massa: ele limpa o contexto de persistência
        // (depois de gravar o que está pendente), e alterar "est" depois disso não seria salvo.
        est.zerarContadores();
        senhas.apagarDoEstabelecimento(est.getId());
        avisarTelas(est.getId());
    }

    /** Rotina diária (RNF06): zera a fila de todos os estabelecimentos. */
    @Transactional
    public void zerarTodas() {
        estabelecimentos.findAll().forEach(e -> zerar(e.getId()));
    }

    // ------------------------------------------------------------------ tela de entrada

    @Transactional(readOnly = true)
    public Optional<Estabelecimento> buscarPorCodigoTela(String codigoTela) {
        return estabelecimentos.findByCodigoTela(codigoTela);
    }

    /** Gera outro link para a tela de entrada; o anterior deixa de funcionar na hora. */
    @Transactional
    public String renovarCodigoTela(Long estabelecimentoId) {
        Estabelecimento est = travar(estabelecimentoId);
        est.renovarCodigoTela();
        return est.getCodigoTela();
    }

    // ------------------------------------------------------------------ internos

    private Estabelecimento travar(Long estabelecimentoId) {
        return estabelecimentos.findByIdForUpdate(estabelecimentoId)
                .orElseThrow(() -> new FilaException("Estabelecimento não encontrado."));
    }

    private Senha exigirChamadaAtual(Long estabelecimentoId) {
        travar(estabelecimentoId);
        return chamadaAtual(estabelecimentoId)
                .orElseThrow(() -> new FilaException("Nenhuma senha está sendo chamada no momento."));
    }

    /** Encerra a senha (finalizada, pulada ou cancelada) e apaga a inscrição de push do aparelho do cliente. */
    private void encerrar(Senha senha, StatusSenha novoStatus) {
        senha.setStatus(novoStatus);
        inscricoes.deleteBySenhaId(senha.getId());
    }

    private void avisarTelas(Long estabelecimentoId) {
        eventos.publishEvent(new FilaAtualizada(estabelecimentoId));
    }
}
