package br.fatec.chamaja.repository;

import br.fatec.chamaja.model.Senha;
import br.fatec.chamaja.model.StatusSenha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SenhaRepository extends JpaRepository<Senha, Long> {

    Optional<Senha> findByToken(String token);

    List<Senha> findByEstabelecimentoIdOrderByNumeroAsc(Long estabelecimentoId);

    List<Senha> findByEstabelecimentoIdAndStatus(Long estabelecimentoId, StatusSenha status);

    /** Próxima a ser chamada: a aguardando de menor número. */
    Optional<Senha> findFirstByEstabelecimentoIdAndStatusOrderByNumeroAsc(Long estabelecimentoId, StatusSenha status);

    /** Senha em atendimento no momento: a chamada de maior número. */
    Optional<Senha> findFirstByEstabelecimentoIdAndStatusOrderByNumeroDesc(Long estabelecimentoId, StatusSenha status);

    long countByEstabelecimentoIdAndStatus(Long estabelecimentoId, StatusSenha status);

    /** Quantas aguardando estão antes do número informado (base do cálculo de posição). */
    long countByEstabelecimentoIdAndStatusAndNumeroLessThan(Long estabelecimentoId, StatusSenha status, int numero);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Senha s where s.estabelecimento.id = :id")
    void apagarDoEstabelecimento(@Param("id") Long estabelecimentoId);
}
