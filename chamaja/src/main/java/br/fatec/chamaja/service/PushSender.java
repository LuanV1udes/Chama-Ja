package br.fatec.chamaja.service;

/** Quem de fato entrega um aviso ao serviço de push do navegador (nos testes, um substituto). */
public interface PushSender {

    enum Resultado {
        /** O serviço de push aceitou a mensagem. */
        ENVIADA,
        /** A inscrição não existe mais (404/410): o cliente desinstalou ou revogou; apague-a. */
        EXPIRADA,
        /** Falha passageira ou recusa; a inscrição continua valendo. */
        ERRO
    }

    /**
     * @param payload JSON que o service worker do navegador vai ler
     * @param urgente "é a sua vez": prioridade alta e validade curta (não adianta avisar tarde)
     */
    Resultado enviar(String endpoint, String p256dh, String auth, String payload, boolean urgente);
}
