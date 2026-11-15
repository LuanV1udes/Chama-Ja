package br.fatec.chamaja.repository;

import br.fatec.chamaja.model.Promocao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PromocaoRepository extends JpaRepository<Promocao, Long> {

    List<Promocao> findByEstabelecimentoIdOrderByTituloAsc(Long estabelecimentoId);

    Optional<Promocao> findByIdAndEstabelecimentoId(Long id, Long estabelecimentoId);

    /** O cliente vê só as promoções ativas e dentro da validade. */
    List<Promocao> findByEstabelecimentoIdAndAtivaTrueAndValidadeGreaterThanEqualOrderByTituloAsc(
            Long estabelecimentoId, LocalDate hoje);
}
