package br.fatec.chamaja.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Entity
@Table(name = "senha", indexes = {
        @Index(name = "idx_senha_estab_status_numero", columnList = "estabelecimento_id, status, numero")
})
public class Senha {

    private static final DateTimeFormatter HORA =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "estabelecimento_id")
    private Estabelecimento estabelecimento;

    /** Identificador público e não adivinhável: é o que o aparelho do cliente guarda (RNF07). */
    @Column(nullable = false, unique = true, length = 36)
    private String token;

    @Column(nullable = false)
    private int numero;

    @Column(name = "nome_cliente", nullable = false, length = 60)
    private String nomeCliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private StatusSenha status = StatusSenha.AGUARDANDO;

    @Column(nullable = false)
    private Instant entrada;

    @Column(name = "chamada_em")
    private Instant chamadaEm;

    /** Já entregamos ao cliente o aviso de que faltam até 3 números (RF05)? */
    @Column(name = "aviso_proximidade_enviado", nullable = false)
    private boolean avisoProximidadeEnviado;

    protected Senha() {
    }

    public Senha(Estabelecimento estabelecimento, int numero, String nomeCliente) {
        this.estabelecimento = estabelecimento;
        this.numero = numero;
        this.nomeCliente = nomeCliente;
        this.token = UUID.randomUUID().toString();
        this.entrada = Instant.now();
    }

    public String getNumeroFormatado() {
        return String.format("%03d", numero);
    }

    public String getEntradaHora() {
        return HORA.format(entrada);
    }

    public Long getId() {
        return id;
    }

    public Estabelecimento getEstabelecimento() {
        return estabelecimento;
    }

    public String getToken() {
        return token;
    }

    public int getNumero() {
        return numero;
    }

    public String getNomeCliente() {
        return nomeCliente;
    }

    public StatusSenha getStatus() {
        return status;
    }

    public void setStatus(StatusSenha status) {
        this.status = status;
    }

    public Instant getEntrada() {
        return entrada;
    }

    public Instant getChamadaEm() {
        return chamadaEm;
    }

    public void setChamadaEm(Instant chamadaEm) {
        this.chamadaEm = chamadaEm;
    }

    public boolean isAvisoProximidadeEnviado() {
        return avisoProximidadeEnviado;
    }

    public void setAvisoProximidadeEnviado(boolean avisoProximidadeEnviado) {
        this.avisoProximidadeEnviado = avisoProximidadeEnviado;
    }
}
