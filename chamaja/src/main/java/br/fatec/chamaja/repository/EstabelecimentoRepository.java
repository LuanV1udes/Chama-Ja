package br.fatec.chamaja.repository;

import br.fatec.chamaja.model.Estabelecimento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EstabelecimentoRepository extends JpaRepository<Estabelecimento, Long> {

    Optional<Estabelecimento> findByCodigoQr(String codigoQr);

    /** Trava a linha para que duas retiradas simultâneas não recebam o mesmo número. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Estabelecimento e where e.codigoQr = :codigo")
    Optional<Estabelecimento> findByCodigoQrForUpdate(@Param("codigo") String codigo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Estabelecimento e where e.id = :id")
    Optional<Estabelecimento> findByIdForUpdate(@Param("id") Long id);
}
