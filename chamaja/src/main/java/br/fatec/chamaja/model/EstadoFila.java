package br.fatec.chamaja.model;

/**
 * Estado da fila do estabelecimento (RF13).
 * ABERTA: aceita novas senhas e chama normalmente.
 * PAUSADA: não aceita novas senhas, mas o operador segue chamando as existentes.
 * FECHADA: não aceita novas senhas e não chama.
 */
public enum EstadoFila {
    ABERTA("Aberta"),
    PAUSADA("Pausada"),
    FECHADA("Fechada");

    private final String rotulo;

    EstadoFila(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
