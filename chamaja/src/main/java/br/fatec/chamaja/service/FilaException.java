package br.fatec.chamaja.service;

/** Erro de regra de negócio da fila; a mensagem é segura para mostrar ao usuário. */
public class FilaException extends RuntimeException {

    public FilaException(String mensagem) {
        super(mensagem);
    }
}
