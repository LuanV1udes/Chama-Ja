package br.fatec.chamaja.config;

import br.fatec.chamaja.model.EstadoFila;
import br.fatec.chamaja.model.Estabelecimento;
import br.fatec.chamaja.model.Operador;
import br.fatec.chamaja.repository.EstabelecimentoRepository;
import br.fatec.chamaja.repository.OperadorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.UUID;

/** Cria o estabelecimento e o operador iniciais quando o banco está vazio (um por conta no MVP). */
@Component
@ConditionalOnProperty(name = "chamaja.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String ALFABETO = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final EstabelecimentoRepository estabelecimentos;
    private final OperadorRepository operadores;
    private final PasswordEncoder encoder;
    private final String nome;
    private final String usuario;
    private final String senhaConfigurada;

    public DataInitializer(EstabelecimentoRepository estabelecimentos,
                           OperadorRepository operadores,
                           PasswordEncoder encoder,
                           @Value("${chamaja.seed.nome}") String nome,
                           @Value("${chamaja.seed.usuario}") String usuario,
                           @Value("${chamaja.seed.senha:}") String senhaConfigurada) {
        this.estabelecimentos = estabelecimentos;
        this.operadores = operadores;
        this.encoder = encoder;
        this.nome = nome;
        this.usuario = usuario;
        this.senhaConfigurada = senhaConfigurada;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (estabelecimentos.count() > 0) {
            return;
        }
        Estabelecimento est = new Estabelecimento(nome, UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        est.setEstadoFila(EstadoFila.ABERTA);
        estabelecimentos.save(est);

        boolean gerada = senhaConfigurada.isBlank();
        String senha = gerada ? aleatoria(14) : senhaConfigurada;
        operadores.save(new Operador(est, usuario, encoder.encode(senha)));

        log.info("Estabelecimento '{}' criado. Página do cliente: /q/{}", est.getNome(), est.getCodigoQr());
        if (gerada) {
            log.warn("Operador '{}' criado com a senha gerada: {}  (defina SEED_SENHA para escolher a sua)",
                    usuario, senha);
        } else {
            log.info("Operador '{}' criado.", usuario);
        }
    }

    private static String aleatoria(int tamanho) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(tamanho);
        for (int i = 0; i < tamanho; i++) {
            sb.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }
}
