
package br.com.gatewayseguro.controller;

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

    public PedidosGatewayController(
            @Value("${app.pedidos.url}") String urlPedidos) {

        this.restClient = RestClient.builder()
                .baseUrl(urlPedidos)
                .build();
    }

    @GetMapping("/api/pedidos")
    public ResponseEntity<String> listarPedidos() {

        try {
            String resposta = restClient.get()
                    .uri("/api/pedidos")
                    .header("X-Gateway-Authenticated", "true")
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
