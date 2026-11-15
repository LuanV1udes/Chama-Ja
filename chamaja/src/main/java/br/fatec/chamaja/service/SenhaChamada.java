package br.fatec.chamaja.service;

/** Evento interno: uma senha foi chamada (ou a chamada foi repetida); o cliente deve ser avisado por push. */
public record SenhaChamada(Long senhaId, boolean repeticao) {
}
