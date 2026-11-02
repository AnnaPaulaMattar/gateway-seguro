
package br.com.gatewayseguro.controller;

import br.com.gatewayseguro.security.GatewayTokenSigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RestController
public class PedidosGatewayController {

    private final RestClient restClient;
    private final GatewayTokenSigner tokenSigner;

    public PedidosGatewayController(
            @Value("${app.pedidos.url}") String urlPedidos,
            RestClient.Builder restClientBuilder,
            GatewayTokenSigner tokenSigner) {

        this.restClient = restClientBuilder
                .baseUrl(urlPedidos)
                .build();

        this.tokenSigner = tokenSigner;
    }

    @GetMapping("/api/pedidos")
    public ResponseEntity<String> listarPedidos() {

        try {
            String tokenInterno = tokenSigner.gerarToken(
                    "GET", "/api/pedidos");

            String resposta = restClient.get()
                    .uri("/api/pedidos")
                    .header("X-Gateway-Authenticated", "true")
                    .header("X-Gateway-Token", tokenInterno)
                    .retrieve()
                    .body(String.class);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(resposta);

        } catch (RestClientException e) {

            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body("{\"erro\":\"FALHA_MICROSSERVICO\","
                            + "\"mensagem\":\"Nao foi possivel consultar os pedidos\"}");
        }
    }
}
