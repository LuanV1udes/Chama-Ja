package br.fatec.chamaja.model;

public enum StatusSenha {
    AGUARDANDO("Aguardando"),
    CHAMADA("Chamada"),
    FINALIZADA("Finalizada"),
    PULADA("Pulada"),
    CANCELADA("Cancelada");

    private final String rotulo;

    StatusSenha(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    /** Senha que ainda faz parte do atendimento do cliente (ele ainda precisa dela). */
    public boolean isAtiva() {
        return this == AGUARDANDO || this == CHAMADA;
    }
}
