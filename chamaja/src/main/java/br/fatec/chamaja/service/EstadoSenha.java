package br.fatec.chamaja.service;

import java.util.List;

/**
 * Tudo que a tela "Minha senha" precisa para se desenhar (RF03 a RF08).
 *
 * @param numero      número da senha do cliente, ex.: "014"
 * @param status      AGUARDANDO, CHAMADA, FINALIZADA, PULADA ou CANCELADA
 * @param posicao     posição na fila (1 = próximo a ser chamado); 0 se não estiver aguardando
 * @param aFrente     quantas pessoas estão à frente; 0 se não estiver aguardando
 * @param senhaAtual  senha sendo chamada agora, ex.: "012" ("---" se ninguém foi chamado)
 * @param proximo     faltam até 3 números para a vez do cliente (RF05)
 * @param novoAviso   true só na primeira vez que o aviso de proximidade é entregue
 * @param chamadaEm   instante (epoch ms) da última chamada da senha; muda a cada "repetir"
 * @param filaEstado  ABERTA, PAUSADA ou FECHADA
 */
public record EstadoSenha(
        String numero,
        String nome,
        String status,
        int posicao,
        int aFrente,
        String senhaAtual,
        boolean proximo,
        boolean novoAviso,
        long chamadaEm,
        String filaEstado,
        List<PromocaoView> promocoes) {
}
