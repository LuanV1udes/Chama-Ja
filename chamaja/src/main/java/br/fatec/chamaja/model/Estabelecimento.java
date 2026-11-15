package br.fatec.chamaja.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "estabelecimento")
public class Estabelecimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(name = "codigo_qr", nullable = false, unique = true, length = 40)
    private String codigoQr;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_fila", nullable = false, length = 10)
    private EstadoFila estadoFila = EstadoFila.FECHADA;

    /** Número da última senha chamada (0 = ninguém chamado ainda). */
    @Column(name = "senha_atual", nullable = false)
    private int senhaAtual;

    /** Último número distribuído; a próxima senha é este + 1. */
    @Column(name = "ultimo_numero", nullable = false)
    private int ultimoNumero;

    protected Estabelecimento() {
    }

    public Estabelecimento(String nome, String codigoQr) {
        this.nome = nome;
        this.codigoQr = codigoQr;
    }

    public int proximoNumero() {
        return ++ultimoNumero;
    }

    public void zerarContadores() {
        senhaAtual = 0;
        ultimoNumero = 0;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getCodigoQr() {
        return codigoQr;
    }

    public EstadoFila getEstadoFila() {
        return estadoFila;
    }

    public void setEstadoFila(EstadoFila estadoFila) {
        this.estadoFila = estadoFila;
    }

    public int getSenhaAtual() {
        return senhaAtual;
    }

    public void setSenhaAtual(int senhaAtual) {
        this.senhaAtual = senhaAtual;
    }

    public int getUltimoNumero() {
        return ultimoNumero;
    }
}
