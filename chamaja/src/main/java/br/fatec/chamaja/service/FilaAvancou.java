package br.fatec.chamaja.service;

/** Evento interno: a senha atual mudou, então quem está a até 3 números pode precisar de um aviso de push. */
public record FilaAvancou(Long estabelecimentoId) {
}
