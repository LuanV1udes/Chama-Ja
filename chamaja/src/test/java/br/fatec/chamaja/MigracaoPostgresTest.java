package br.fatec.chamaja;

import br.fatec.chamaja.model.EstadoFila;
import br.fatec.chamaja.model.Estabelecimento;
import br.fatec.chamaja.repository.EstabelecimentoRepository;
import br.fatec.chamaja.service.FilaService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sobe o perfil "prod" (Flyway + Hibernate em modo "validate") num PostgreSQL de verdade.
 * Se uma migração não bater com as entidades, a aplicação nem inicia e este teste falha.
 */
@SpringBootTest(properties = {
        "chamaja.seed.senha=senha-de-teste-123",
        "chamaja.reset-cron=-"   // desliga a rotina diária neste teste
})
@ActiveProfiles("prod")
class MigracaoPostgresTest {

    private static final EmbeddedPostgres POSTGRES = iniciar();

    private static EmbeddedPostgres iniciar() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível iniciar o PostgreSQL de teste", e);
        }
    }

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @AfterAll
    static void parar() throws IOException {
        POSTGRES.close();
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FilaService fila;

    @Autowired
    EstabelecimentoRepository estabelecimentos;

    @Test
    void asTabelasFicamNoSchemaChamajaENaoNoPublic() {
        List<String> noChamaja = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'chamaja'", String.class);
        assertThat(noChamaja).contains("estabelecimento", "operador", "senha", "promocao", "flyway_schema_history");

        Integer noPublic = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public'", Integer.class);
        assertThat(noPublic).isZero();
    }

    @Test
    void rlsEstaLigadoEmTodasAsTabelas() {
        List<Boolean> rls = jdbc.queryForList("""
                select c.relrowsecurity from pg_class c
                join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'chamaja' and c.relname in ('estabelecimento','operador','senha','promocao')
                """, Boolean.class);
        assertThat(rls).hasSize(4).containsOnly(true);
    }

    @Test
    void osDadosIniciaisForamCriadosPeloSeed() {
        Integer operadores = jdbc.queryForObject("select count(*) from chamaja.operador", Integer.class);
        assertThat(operadores).isEqualTo(1);
    }

    @Test
    void oEstabelecimentoDoSeedTemCodigoDaTela() {
        String codigoTela = jdbc.queryForObject(
                "select codigo_tela from chamaja.estabelecimento order by id limit 1", String.class);
        assertThat(codigoTela).hasSize(32);
    }

    /**
     * O banco de produção já tinha estabelecimentos quando a V2 foi criada. Aqui: aplica só a V1,
     * insere dados "antigos" e só então aplica a V2; todos precisam ganhar um código único.
     */
    @Test
    void aV2PreencheOCodigoDaTelaDosEstabelecimentosQueJaExistiam() {
        jdbc.execute("create database antigo");
        var banco = new DriverManagerDataSource(POSTGRES.getJdbcUrl("postgres", "antigo"), "postgres", "postgres");

        Flyway.configure().dataSource(banco).schemas("chamaja").defaultSchema("chamaja")
                .createSchemas(true).target("1").load().migrate();

        var antigo = new JdbcTemplate(banco);
        antigo.update("insert into chamaja.estabelecimento (nome, codigo_qr, estado_fila, senha_atual, ultimo_numero) "
                + "values ('Loja A', 'lojaa', 'ABERTA', 0, 0), ('Loja B', 'lojab', 'FECHADA', 3, 5)");

        Flyway.configure().dataSource(banco).schemas("chamaja").defaultSchema("chamaja").load().migrate();

        List<String> codigos = antigo.queryForList("select codigo_tela from chamaja.estabelecimento", String.class);
        assertThat(codigos).hasSize(2).doesNotContainNull().doesNotHaveDuplicates()
                .allSatisfy(c -> assertThat(c).hasSize(32));
        assertThat(antigo.queryForObject("select count(*) from chamaja.estabelecimento where nome = 'Loja B' "
                + "and senha_atual = 3 and ultimo_numero = 5", Integer.class)).isEqualTo(1);
    }

    @Test
    void ciclodaFilaFuncionaComAsMigracoes() {
        String codigo = UUID.randomUUID().toString().substring(0, 12);
        Estabelecimento est = new Estabelecimento("Teste Postgres", codigo);
        est.setEstadoFila(EstadoFila.ABERTA);
        est = estabelecimentos.save(est);

        var primeira = fila.retirar(codigo, "Ana");
        var segunda = fila.retirar(codigo, "Bruno");
        assertThat(primeira.getNumeroFormatado()).isEqualTo("001");
        assertThat(segunda.getNumeroFormatado()).isEqualTo("002");

        assertThat(fila.chamarProxima(est.getId()).getNumero()).isEqualTo(1);
        assertThat(fila.estado(segunda.getToken()).orElseThrow().posicao()).isEqualTo(1);

        fila.zerar(est.getId());
        assertThat(fila.listar(est.getId())).isEmpty();
        assertThat(fila.retirar(codigo, "Nova").getNumeroFormatado()).isEqualTo("001");
    }
}
