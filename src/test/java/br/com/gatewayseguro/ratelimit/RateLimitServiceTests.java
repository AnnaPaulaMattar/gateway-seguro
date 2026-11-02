
package br.com.gatewayseguro.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitServiceTests {

    @Test
    void deveBloquearIpAposAtingirLimite() {
        RateLimitService service =
                new RateLimitService(3, 2, 60);

        assertTrue(service.permitirIp("192.168.1.10"));
        assertTrue(service.permitirIp("192.168.1.10"));
        assertTrue(service.permitirIp("192.168.1.10"));

        assertFalse(service.permitirIp("192.168.1.10"));
    }

    @Test
    void deveBloquearUsuarioAposAtingirLimite() {
        RateLimitService service =
                new RateLimitService(30, 2, 60);

        assertTrue(service.permitirUsuario("usuario-teste"));
        assertTrue(service.permitirUsuario("usuario-teste"));

        assertFalse(service.permitirUsuario("usuario-teste"));
    }

    @Test
    void deveManterContadoresSeparadosPorIp() {
        RateLimitService service =
                new RateLimitService(1, 20, 60);

        assertTrue(service.permitirIp("192.168.1.10"));
        assertFalse(service.permitirIp("192.168.1.10"));

        assertTrue(service.permitirIp("192.168.1.20"));
    }

    @Test
    void deveReiniciarContadorAposExpirarJanela()
            throws Exception {

        RateLimitService service =
                new RateLimitService(1, 1, 1);

        assertTrue(service.permitirIp("192.168.1.10"));
        assertFalse(service.permitirIp("192.168.1.10"));

        Thread.sleep(2100);

        assertTrue(service.permitirIp("192.168.1.10"));
    }
}
