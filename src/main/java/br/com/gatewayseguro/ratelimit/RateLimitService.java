
package br.com.gatewayseguro.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Service
public class RateLimitService {

    private static final Logger logger =
            LoggerFactory.getLogger(RateLimitService.class);

    private final int limiteIp;
    private final int limiteUsuario;
    private final long janelaSegundos;

    private final ConcurrentHashMap<String, Contador> contadores =
            new ConcurrentHashMap<>();

    public RateLimitService(
            @Value("${app.rate-limit.ip.max-requests}") int limiteIp,
            @Value("${app.rate-limit.user.max-requests}") int limiteUsuario,
            @Value("${app.rate-limit.window-seconds}") long janelaSegundos) {

        this.limiteIp = limiteIp;
        this.limiteUsuario = limiteUsuario;
        this.janelaSegundos = janelaSegundos;
    }

    public boolean permitirIp(String ip) {
        return permitir("IP:" + ip, limiteIp);
    }

    public boolean permitirUsuario(String usuario) {
        return permitir("USER:" + usuario, limiteUsuario);
    }

    private boolean permitir(String chave, int limite) {

        long agora = Instant.now().getEpochSecond();

        Contador contador = contadores.compute(chave, (key, atual) -> {

            if (atual == null ||
                    agora - atual.inicioJanela >= janelaSegundos) {
                return new Contador(agora, 1);
            }

            return new Contador(
                    atual.inicioJanela,
                    atual.quantidade + 1
            );
        });

        return contador.quantidade <= limite;
    }


    @Scheduled(fixedRate = 60000)
    public void limparContadoresExpirados() {

        long agora = Instant.now().getEpochSecond();

        int antes = contadores.size();

        contadores.entrySet().removeIf(entrada ->
                agora - entrada.getValue().inicioJanela()
                        >= janelaSegundos
        );

        int depois = contadores.size();

        int removidos = antes - depois;

        logger.debug(
                "Rate Limiting - Limpeza executada. Contadores removidos: {} | Contadores restantes: {}",
                removidos,
                depois
        );
    }


    private record Contador(long inicioJanela, int quantidade) {
    }
}
