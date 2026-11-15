package br.fatec.chamaja.repository;

import br.fatec.chamaja.model.InscricaoPush;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InscricaoPushRepository extends JpaRepository<InscricaoPush, Long> {

    Optional<InscricaoPush> findBySenhaId(Long senhaId);

    List<InscricaoPush> findBySenhaIdIn(Collection<Long> senhaIds);

    /**
     * Apaga a inscrição da senha. É uma exclusão derivada (carrega e remove a entidade), de propósito:
     * um DELETE em massa com clearAutomatically soltaria do contexto as outras entidades da
     * transação (a senha e o estabelecimento que o chamador ainda vai alterar).
     */
    void deleteBySenhaId(Long senhaId);

    /** O mesmo aparelho pode ter uma inscrição de uma senha antiga: ela cede lugar à nova. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from InscricaoPush i where i.endpoint = :endpoint")
    void apagarPorEndpoint(@Param("endpoint") String endpoint);
}
