package br.fatec.chamaja.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RNF05: limita as requisições que alteram dados (retirar senha, sair da fila, login) por IP.
 * Janela fixa de 1 minuto, em memória: suficiente para uma instância só.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    static final int LIMITE_POR_MINUTO = 30;
    private static final long JANELA_MS = 60_000L;

    private record Janela(long inicio, AtomicInteger contagem) {
    }

    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String caminho = request.getRequestURI();
        return !(caminho.startsWith("/q/") || caminho.startsWith("/senha/") || caminho.equals("/login"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long agora = System.currentTimeMillis();
        if (janelas.size() > 10_000) {
            janelas.values().removeIf(j -> agora - j.inicio() > JANELA_MS);
        }

        Janela janela = janelas.compute(request.getRemoteAddr(), (ip, atual) ->
                atual == null || agora - atual.inicio() > JANELA_MS ? new Janela(agora, new AtomicInteger()) : atual);

        if (janela.contagem().incrementAndGet() > LIMITE_POR_MINUTO) {
            response.setStatus(429);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Muitas tentativas. Aguarde um minuto e tente de novo.");
            return;
        }
        chain.doFilter(request, response);
    }
}
