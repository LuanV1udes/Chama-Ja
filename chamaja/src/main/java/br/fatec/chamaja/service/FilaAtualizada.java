package br.fatec.chamaja.service;

/** Evento interno: algo mudou na fila do estabelecimento, as telas abertas devem atualizar. */
public record FilaAtualizada(Long estabelecimentoId) {
}
