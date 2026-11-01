
package br.com.gatewayseguro.config;

import java.io.InputStream;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPublicKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${app.jwt.issuer}") String issuer) throws Exception {

        ClassPathResource resource =
                new ClassPathResource("keys/jwt-public.crt");

        Certificate certificate;

        try (InputStream inputStream = resource.getInputStream()) {

            CertificateFactory certificateFactory =
                    CertificateFactory.getInstance("X.509");

            certificate =
                    certificateFactory.generateCertificate(inputStream);
        }

        PublicKey publicKey = certificate.getPublicKey();

        RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(rsaPublicKey)
                .build();

        OAuth2TokenValidator<Jwt> validator =
                JwtValidators.createDefaultWithIssuer(issuer);

        decoder.setJwtValidator(validator);

        return decoder;
    }
}
