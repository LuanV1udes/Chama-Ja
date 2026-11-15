package br.fatec.chamaja.service;

/**
 * Promoção já formatada para a tela do cliente. Com banner ({@code imagem} preenchida) a tela mostra só a
 * imagem; sem banner, mostra o cartão padrão (título, descrição, preço, validade).
 *
 * @param imagem  endereço do banner (com a versão, para o cache), ou null se não houver banner
 * @param largura largura do banner em pixels (o navegador reserva o espaço e a página não "pula"), ou null
 * @param altura  altura do banner em pixels, ou null
 */
public record PromocaoView(String titulo, String descricao, String preco, String validade,
                           String imagem, Integer largura, Integer altura) {
}
