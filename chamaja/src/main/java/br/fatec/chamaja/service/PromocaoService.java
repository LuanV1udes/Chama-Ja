package br.fatec.chamaja.service;

import br.fatec.chamaja.model.Estabelecimento;
import br.fatec.chamaja.model.Promocao;
import br.fatec.chamaja.model.PromocaoImagem;
import br.fatec.chamaja.repository.EstabelecimentoRepository;
import br.fatec.chamaja.repository.PromocaoImagemRepository;
import br.fatec.chamaja.repository.PromocaoRepository;
import br.fatec.chamaja.web.PromocaoForm;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Cadastro de promoções (RF14 e RF15). Toda operação do operador é restrita ao estabelecimento dele.
 *
 * <p>Uma promoção tem o cartão padrão (título, descrição, preço) ou, se o mercado já tem o banner pronto,
 * a imagem do banner, que o cliente vê sozinha no lugar do cartão.
 */
@Service
public class PromocaoService {

    private final PromocaoRepository promocoes;
    private final PromocaoImagemRepository imagens;
    private final EstabelecimentoRepository estabelecimentos;
    private final ProcessadorDeImagem processador;
    private final ApplicationEventPublisher eventos;

    public PromocaoService(PromocaoRepository promocoes,
                           PromocaoImagemRepository imagens,
                           EstabelecimentoRepository estabelecimentos,
                           ProcessadorDeImagem processador,
                           ApplicationEventPublisher eventos) {
        this.promocoes = promocoes;
        this.imagens = imagens;
        this.estabelecimentos = estabelecimentos;
        this.processador = processador;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public List<Promocao> listar(Long estabelecimentoId) {
        return promocoes.findByEstabelecimentoIdOrderByTituloAsc(estabelecimentoId);
    }

    @Transactional(readOnly = true)
    public Promocao buscar(Long estabelecimentoId, Long id) {
        return promocoes.findByIdAndEstabelecimentoId(id, estabelecimentoId)
                .orElseThrow(() -> new FilaException("Promoção não encontrada."));
    }

    @Transactional
    public Promocao salvar(Long estabelecimentoId, PromocaoForm form) {
        Promocao promocao;
        if (form.getId() == null) {
            Estabelecimento est = estabelecimentos.findById(estabelecimentoId)
                    .orElseThrow(() -> new FilaException("Estabelecimento não encontrado."));
            promocao = new Promocao(est);
        } else {
            promocao = buscar(estabelecimentoId, form.getId());
        }

        MultipartFile arquivo = form.getImagem();
        boolean enviouImagem = arquivo != null && !arquivo.isEmpty();
        boolean removeImagem = promocao.temImagem() && form.isRemoverImagem() && !enviouImagem;
        boolean ficaComImagem = enviouImagem || (promocao.temImagem() && !removeImagem);

        // Sem banner, o cartão padrão precisa do preço. Com banner, a arte já traz o preço.
        if (!ficaComImagem && form.getPreco() == null) {
            throw new PromocaoInvalida("preco", "Informe o preço ou envie o banner da promoção.");
        }
        // A imagem é conferida ANTES de qualquer gravação: um arquivo ruim não deixa a promoção pela metade.
        ProcessadorDeImagem.ImagemProcessada nova = enviouImagem ? processador.processar(bytesDe(arquivo)) : null;

        promocao.setTitulo(form.getTitulo().trim());
        promocao.setDescricao(form.getDescricao() == null ? "" : form.getDescricao().trim());
        promocao.setPreco(form.getPreco());
        promocao.setValidade(form.getValidade());
        Promocao salva = promocoes.save(promocao);

        if (nova != null) {
            imagens.save(new PromocaoImagem(salva.getId(), nova.jpeg()));
            salva.registrarImagem(nova.largura(), nova.altura());
        } else if (removeImagem) {
            imagens.deleteById(salva.getId());
            salva.limparImagem();
        }
        avisarTelas(estabelecimentoId);
        return salva;
    }

    @Transactional
    public void alternarAtiva(Long estabelecimentoId, Long id) {
        Promocao promocao = buscar(estabelecimentoId, id);
        promocao.setAtiva(!promocao.isAtiva());
        avisarTelas(estabelecimentoId);
    }

    @Transactional
    public void excluir(Long estabelecimentoId, Long id) {
        Promocao promocao = buscar(estabelecimentoId, id);
        imagens.deleteById(id);   // o banner some junto (no banco, também por ON DELETE CASCADE)
        promocoes.delete(promocao);
        avisarTelas(estabelecimentoId);
    }

    // ------------------------------------------------------------------ banner

    /** Banner de uma promoção do operador (o painel mostra mesmo as desativadas, para a prévia). */
    @Transactional(readOnly = true)
    public byte[] imagemDoOperador(Long estabelecimentoId, Long id) {
        Promocao promocao = buscar(estabelecimentoId, id);
        if (!promocao.temImagem()) {
            throw new FilaException("Esta promoção não tem banner.");
        }
        return bytesDaImagem(id).orElseThrow(() -> new FilaException("Banner não encontrado."));
    }

    /** Promoção que o cliente pode ver agora (ativa e dentro da validade) e que tem banner. */
    @Transactional(readOnly = true)
    public Optional<Promocao> publicaComImagem(Long id) {
        return promocoes.findById(id)
                .filter(p -> p.isAtiva() && !p.isVencida() && p.temImagem());
    }

    @Transactional(readOnly = true)
    public Optional<byte[]> bytesDaImagem(Long id) {
        return imagens.findById(id).map(PromocaoImagem::getDados);
    }

    private static byte[] bytesDe(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new PromocaoInvalida("imagem", "Não foi possível ler o arquivo enviado. Tente de novo.");
        }
    }

    /** As telas dos clientes mostram as promoções ativas, então também se atualizam aqui. */
    private void avisarTelas(Long estabelecimentoId) {
        eventos.publishEvent(new FilaAtualizada(estabelecimentoId));
    }
}
