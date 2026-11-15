package br.fatec.chamaja.repository;

import br.fatec.chamaja.model.Operador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OperadorRepository extends JpaRepository<Operador, Long> {

    Optional<Operador> findByUsuario(String usuario);
}
