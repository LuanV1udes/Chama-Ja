package br.fatec.chamaja.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
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

    private static final long JANELA_MS = 60_000L;

    private final int limitePorMinuto;

    public RateLimitFilter(@Value("${chamaja.rate-limit.por-minuto:30}") int limitePorMinuto) {
        this.limitePorMinuto = limitePorMinuto;
    }

    private record Janela(long inicio, AtomicInteger contagem) {
    }

    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String caminho = request.getRequestURI();
        // Escanear o QR (GET /q/{codigo}?t=...) conta: cada leitura válida libera uma permissão.
        boolean escaneando = "GET".equals(request.getMethod())
                && caminho.startsWith("/q/") && request.getParameter("t") != null;
        boolean alterando = "POST".equals(request.getMethod())
                && (caminho.startsWith("/q/") || caminho.startsWith("/senha/") || caminho.equals("/login"));
        return !(escaneando || alterando);
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

        if (janela.contagem().incrementAndGet() > limitePorMinuto) {
            response.setStatus(429);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Muitas tentativas. Aguarde um minuto e tente de novo.");
            return;
        }
        chain.doFilter(request, response);
    }
}
