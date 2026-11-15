package br.fatec.chamaja;

import br.fatec.chamaja.model.EstadoFila;
import br.fatec.chamaja.model.Estabelecimento;
import br.fatec.chamaja.model.StatusSenha;
import br.fatec.chamaja.repository.EstabelecimentoRepository;
import br.fatec.chamaja.service.EstadoSenha;
import br.fatec.chamaja.service.FilaException;
import br.fatec.chamaja.service.FilaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "chamaja.seed.enabled=false")
@ActiveProfiles("dev")
class FilaServiceTest {

    @Autowired
    FilaService fila;

    @Autowired
    EstabelecimentoRepository estabelecimentos;

    Estabelecimento est;
    String codigo;

    @BeforeEach
    void criarEstabelecimento() {
        codigo = UUID.randomUUID().toString().substring(0, 12);
        est = new Estabelecimento("Mercado Teste", codigo);
        est.setEstadoFila(EstadoFila.ABERTA);
        est = estabelecimentos.save(est);
    }

    /** Retira n senhas e devolve os tokens, na ordem. */
    private List<String> retirar(int quantidade) {
        List<String> tokens = new ArrayList<>();
        for (int i = 1; i <= quantidade; i++) {
            tokens.add(fila.retirar(codigo, "Cliente " + i).getToken());
        }
        return tokens;
    }

    private EstadoSenha estado(String token) {
        return fila.estado(token).orElseThrow();
    }

    @Test
    void numerosSaoSequenciaisComTresDigitos() {
        var primeira = fila.retirar(codigo, "Ana");
        var segunda = fila.retirar(codigo, "Bruno");

        assertThat(primeira.getNumeroFormatado()).isEqualTo("001");
        assertThat(segunda.getNumeroFormatado()).isEqualTo("002");
    }

    @Test
    void posicaoEQuantidadeAFrenteSaoCalculadasPeloNumero() {
        List<String> tokens = retirar(3);

        assertThat(estado(tokens.get(0)).posicao()).isEqualTo(1);
        assertThat(estado(tokens.get(0)).aFrente()).isZero();
        assertThat(estado(tokens.get(2)).posicao()).isEqualTo(3);
        assertThat(estado(tokens.get(2)).aFrente()).isEqualTo(2);
    }

    @Test
    void avisoDeProximidadeDisparaComAteTresNumerosDeDiferenca() {
        List<String> tokens = retirar(8);

        // Ninguém chamado ainda (atual = 0): senhas 1, 2 e 3 estão a até 3 números.
        assertThat(estado(tokens.get(0)).proximo()).isTrue();
        assertThat(estado(tokens.get(2)).proximo()).isTrue();
        assertThat(estado(tokens.get(3)).proximo()).isFalse();

        // Chama a 001: agora 002, 003 e 004 estão a até 3 números; a 005 não.
        fila.chamarProxima(est.getId());
        assertThat(estado(tokens.get(3)).proximo()).isTrue();   // 004: diferença 3
        assertThat(estado(tokens.get(4)).proximo()).isFalse();  // 005: diferença 4

        // Chama a 002: a 005 passa a ser avisada (diferença 3).
        fila.chamarProxima(est.getId());
        assertThat(estado(tokens.get(4)).proximo()).isTrue();
    }

    @Test
    void avisoDeProximidadeEEntregueUmaUnicaVez() {
        List<String> tokens = retirar(6);
        fila.chamarProxima(est.getId()); // atual = 001

        String token = tokens.get(3); // 004: diferença 3
        assertThat(estado(token).novoAviso()).isTrue();
        assertThat(estado(token).novoAviso()).isFalse();
        assertThat(estado(token).proximo()).isTrue(); // o banner continua valendo
    }

    @Test
    void clienteDistanteNaoRecebeAvisoDeProximidade() {
        List<String> tokens = retirar(10);

        EstadoSenha ultimo = estado(tokens.get(9)); // 010, atual = 0
        assertThat(ultimo.proximo()).isFalse();
        assertThat(ultimo.novoAviso()).isFalse();
    }

    @Test
    void chamarProximaSegueAOrdemEFinalizaAAnterior() {
        List<String> tokens = retirar(3);

        var primeira = fila.chamarProxima(est.getId());
        assertThat(primeira.getNumero()).isEqualTo(1);
        assertThat(estado(tokens.get(0)).status()).isEqualTo("CHAMADA");
        assertThat(estado(tokens.get(0)).chamadaEm()).isPositive();

        var segunda = fila.chamarProxima(est.getId());
        assertThat(segunda.getNumero()).isEqualTo(2);
        assertThat(estado(tokens.get(0)).status()).isEqualTo("FINALIZADA");
        assertThat(estado(tokens.get(1)).status()).isEqualTo("CHAMADA");
        assertThat(estado(tokens.get(1)).senhaAtual()).isEqualTo("002");
    }

    @Test
    void repetirMudaOHorarioDaChamadaSemMudarOStatus() throws InterruptedException {
        List<String> tokens = retirar(1);
        fila.chamarProxima(est.getId());
        long antes = estado(tokens.get(0)).chamadaEm();

        Thread.sleep(5);
        fila.repetir(est.getId());

        EstadoSenha depois = estado(tokens.get(0));
        assertThat(depois.status()).isEqualTo("CHAMADA");
        assertThat(depois.chamadaEm()).isGreaterThan(antes);
    }

    @Test
    void pularEFinalizarAtuamNaSenhaAtual() {
        List<String> tokens = retirar(2);

        fila.chamarProxima(est.getId());
        fila.pular(est.getId());
        assertThat(estado(tokens.get(0)).status()).isEqualTo("PULADA");

        fila.chamarProxima(est.getId());
        fila.finalizar(est.getId());
        assertThat(estado(tokens.get(1)).status()).isEqualTo("FINALIZADA");
    }

    @Test
    void semSenhaEmAtendimentoRepetirPularEFinalizarFalham() {
        assertThatThrownBy(() -> fila.repetir(est.getId())).isInstanceOf(FilaException.class);
        assertThatThrownBy(() -> fila.pular(est.getId())).isInstanceOf(FilaException.class);
        assertThatThrownBy(() -> fila.finalizar(est.getId())).isInstanceOf(FilaException.class);
    }

    @Test
    void chamarProximaSemAguardandoFalha() {
        assertThatThrownBy(() -> fila.chamarProxima(est.getId()))
                .isInstanceOf(FilaException.class)
                .hasMessageContaining("aguardando");
    }

    @Test
    void filaPausadaNaoAceitaSenhasMasContinuaChamando() {
        retirar(2);
        fila.definirEstado(est.getId(), EstadoFila.PAUSADA);

        assertThatThrownBy(() -> fila.retirar(codigo, "Carla")).isInstanceOf(FilaException.class);
        assertThat(fila.chamarProxima(est.getId()).getNumero()).isEqualTo(1);
    }

    @Test
    void filaFechadaNaoAceitaSenhasNemChama() {
        retirar(1);
        fila.definirEstado(est.getId(), EstadoFila.FECHADA);

        assertThatThrownBy(() -> fila.retirar(codigo, "Carla")).isInstanceOf(FilaException.class);
        assertThatThrownBy(() -> fila.chamarProxima(est.getId())).isInstanceOf(FilaException.class);
    }

    @Test
    void nomeEmBrancoEhRejeitado() {
        assertThatThrownBy(() -> fila.retirar(codigo, "   "))
                .isInstanceOf(FilaException.class)
                .hasMessageContaining("nome");
    }

    @Test
    void sairDaFilaAtualizaAPosicaoDosDemais() {
        List<String> tokens = retirar(3);

        fila.cancelar(tokens.get(0));

        assertThat(estado(tokens.get(0)).status()).isEqualTo("CANCELADA");
        assertThat(estado(tokens.get(1)).posicao()).isEqualTo(1);
        assertThat(estado(tokens.get(2)).posicao()).isEqualTo(2);
    }

    @Test
    void naoEPossivelSairDaFilaDepoisDeChamado() {
        List<String> tokens = retirar(1);
        fila.chamarProxima(est.getId());

        assertThatThrownBy(() -> fila.cancelar(tokens.get(0))).isInstanceOf(FilaException.class);
        assertThat(estado(tokens.get(0)).status()).isEqualTo(StatusSenha.CHAMADA.name());
    }

    @Test
    void zerarApagaAsSenhasEReiniciaANumeracao() {
        List<String> tokens = retirar(3);
        fila.chamarProxima(est.getId());

        fila.zerar(est.getId());

        assertThat(fila.estado(tokens.get(0))).isEmpty();
        assertThat(fila.listar(est.getId())).isEmpty();
        assertThat(fila.retirar(codigo, "Nova").getNumeroFormatado()).isEqualTo("001");
        assertThat(estabelecimentos.findById(est.getId()).orElseThrow().getSenhaAtual()).isZero();
    }
}
