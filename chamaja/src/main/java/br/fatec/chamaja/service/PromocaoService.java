package br.fatec.chamaja.service;

import br.fatec.chamaja.model.Estabelecimento;
import br.fatec.chamaja.model.Promocao;
import br.fatec.chamaja.repository.EstabelecimentoRepository;
import br.fatec.chamaja.repository.PromocaoRepository;
import br.fatec.chamaja.web.PromocaoForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Cadastro de promoções (RF14 e RF15). Toda operação é restrita ao estabelecimento do operador. */
@Service
public class PromocaoService {

    private final PromocaoRepository promocoes;
    private final EstabelecimentoRepository estabelecimentos;
    private final org.springframework.context.ApplicationEventPublisher eventos;

    public PromocaoService(PromocaoRepository promocoes,
                           EstabelecimentoRepository estabelecimentos,
                           org.springframework.context.ApplicationEventPublisher eventos) {
        this.promocoes = promocoes;
        this.estabelecimentos = estabelecimentos;
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
        promocao.setTitulo(form.getTitulo().trim());
        promocao.setDescricao(form.getDescricao() == null ? "" : form.getDescricao().trim());
        promocao.setPreco(form.getPreco());
        promocao.setValidade(form.getValidade());
        Promocao salva = promocoes.save(promocao);
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
        promocoes.delete(buscar(estabelecimentoId, id));
        avisarTelas(estabelecimentoId);
    }

    /** As telas dos clientes mostram as promoções ativas, então também se atualizam aqui. */
    private void avisarTelas(Long estabelecimentoId) {
        eventos.publishEvent(new FilaAtualizada(estabelecimentoId));
    }
}
