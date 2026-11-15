package br.fatec.chamaja.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * O arquivo do banner de uma promoção (JPEG já reduzido e comprimido). Fica em tabela separada da
 * promoção para que listar promoções nunca carregue os bytes da imagem.
 */
@Entity
@Table(name = "promocao_imagem")
public class PromocaoImagem {

    /** Mesmo id da promoção (relação 1 para 1). */
    @Id
    @Column(name = "promocao_id")
    private Long promocaoId;

    @Column(nullable = false, length = 1_000_000)
    private byte[] dados;

    @Column(nullable = false)
    private int tamanho;

    @Column(name = "atualizada_em", nullable = false)
    private Instant atualizadaEm = Instant.now();

    protected PromocaoImagem() {
    }

    public PromocaoImagem(Long promocaoId, byte[] dados) {
        this.promocaoId = promocaoId;
        this.dados = dados;
        this.tamanho = dados.length;
    }

    public Long getPromocaoId() {
        return promocaoId;
    }

    public byte[] getDados() {
        return dados;
    }

    public int getTamanho() {
        return tamanho;
    }

    public Instant getAtualizadaEm() {
        return atualizadaEm;
    }
}
