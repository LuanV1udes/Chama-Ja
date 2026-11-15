package br.fatec.chamaja.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "promocao")
public class Promocao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "estabelecimento_id")
    private Estabelecimento estabelecimento;

    @Column(nullable = false, length = 100)
    private String titulo;

    @Column(length = 500)
    private String descricao;

    /** Opcional quando a promoção tem banner (a arte já traz o preço). */
    @Column(precision = 10, scale = 2)
    private BigDecimal preco;

    @Column(nullable = false)
    private LocalDate validade;

    @Column(nullable = false)
    private boolean ativa = true;

    /**
     * Dados leves do banner (o arquivo fica em PromocaoImagem). A versão é o instante do envio: muda a cada
     * troca de imagem e serve para o navegador guardar a imagem em cache sem nunca mostrar uma antiga.
     */
    @Column(name = "imagem_versao")
    private Long imagemVersao;

    @Column(name = "imagem_largura")
    private Integer imagemLargura;

    @Column(name = "imagem_altura")
    private Integer imagemAltura;

    protected Promocao() {
    }

    public boolean temImagem() {
        return imagemVersao != null;
    }

    public void registrarImagem(int largura, int altura) {
        this.imagemVersao = System.currentTimeMillis();
        this.imagemLargura = largura;
        this.imagemAltura = altura;
    }

    public void limparImagem() {
        this.imagemVersao = null;
        this.imagemLargura = null;
        this.imagemAltura = null;
    }

    public Long getImagemVersao() {
        return imagemVersao;
    }

    public Integer getImagemLargura() {
        return imagemLargura;
    }

    public Integer getImagemAltura() {
        return imagemAltura;
    }

    public Promocao(Estabelecimento estabelecimento) {
        this.estabelecimento = estabelecimento;
    }

    public boolean isVencida() {
        return validade.isBefore(LocalDate.now());
    }

    public Long getId() {
        return id;
    }

    public Estabelecimento getEstabelecimento() {
        return estabelecimento;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }

    public LocalDate getValidade() {
        return validade;
    }

    public void setValidade(LocalDate validade) {
        this.validade = validade;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public void setAtiva(boolean ativa) {
        this.ativa = ativa;
    }
}
