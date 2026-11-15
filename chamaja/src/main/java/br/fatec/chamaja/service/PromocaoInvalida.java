package br.fatec.chamaja.service;

/** Dado da promoção recusado; a mensagem aparece junto do campo (preço, imagem...) no formulário. */
public class PromocaoInvalida extends FilaException {

    private final String campo;

    public PromocaoInvalida(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
