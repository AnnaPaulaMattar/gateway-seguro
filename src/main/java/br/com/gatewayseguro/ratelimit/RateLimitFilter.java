
package br.com.gatewayseguro.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    public RateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String ip = request.getRemoteAddr();

        if (!rateLimitService.permitirIp(ip)) {
            bloquear(response);
            return;
        }

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {

            String usuario = authentication.getName();

            if (!rateLimitService.permitirUsuario(usuario)) {
                bloquear(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void bloquear(HttpServletResponse response)
            throws IOException {

        response.setStatus(429);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write(
                "{\"erro\":\"LIMITE_EXCEDIDO\","
                        + "\"mensagem\":\"Muitas requisicoes. Tente novamente mais tarde.\"}"
        );
    }
}
