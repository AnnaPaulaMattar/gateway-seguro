
package br.com.gatewayseguro.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Component
public class GatewayTokenSigner {

    private final byte[] chaveSecreta;
    private final SecureRandom random = new SecureRandom();

    public GatewayTokenSigner(
            @Value("${gateway.internal.secret}") String segredo) {

        this.chaveSecreta = Base64.getDecoder().decode(segredo);

        if (chaveSecreta.length < 32) {
            throw new IllegalArgumentException(
                    "A chave interna deve ter pelo menos 256 bits");
        }
    }

    public String gerarToken(String metodo, String rota) {
        try {
            String timestamp = String.valueOf(
                    Instant.now().getEpochSecond());

            byte[] bytesNonce = new byte[16];
            random.nextBytes(bytesNonce);

            String nonce = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(bytesNonce);

            String mensagem = timestamp + "\n"
                    + nonce + "\n"
                    + metodo + "\n"
                    + rota;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    chaveSecreta, "HmacSHA256"));

            byte[] assinatura = mac.doFinal(
                    mensagem.getBytes(StandardCharsets.UTF_8));

            String assinaturaCodificada =
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(assinatura);

            return timestamp + "." + nonce + "."
                    + assinaturaCodificada;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Falha ao gerar credencial interna", e);
        }
    }
}
