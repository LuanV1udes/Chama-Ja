package br.fatec.chamaja.web;

import br.fatec.chamaja.model.Promocao;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Formulário de promoção (RF14): título, descrição, preço e validade. */
public class PromocaoForm {

    private Long id;

    @NotBlank(message = "Informe o título.")
    @Size(max = 100, message = "O título pode ter até 100 caracteres.")
    private String titulo;

    @Size(max = 500, message = "A descrição pode ter até 500 caracteres.")
    private String descricao;

    @NotNull(message = "Informe o preço.")
    @DecimalMin(value = "0.00", message = "O preço não pode ser negativo.")
    @Digits(integer = 8, fraction = 2, message = "Use no máximo 2 casas decimais.")
    private BigDecimal preco;

    @NotNull(message = "Informe a validade.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validade;

    public static PromocaoForm de(Promocao p) {
        PromocaoForm form = new PromocaoForm();
        form.id = p.getId();
        form.titulo = p.getTitulo();
        form.descricao = p.getDescricao();
        form.preco = p.getPreco();
        form.validade = p.getValidade();
        return form;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
}
