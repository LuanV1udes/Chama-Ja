package br.fatec.chamaja.service;

/** Promoção já formatada para exibição na tela do cliente. */
public record PromocaoView(String titulo, String descricao, String preco, String validade) {
}
