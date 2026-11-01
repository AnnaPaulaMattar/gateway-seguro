
package br.com.gatewayseguro;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.test.web.client.MockRestServiceServer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import org.springframework.beans.factory.annotation.Qualifier;


import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import org.junit.jupiter.api.BeforeEach;



@SpringBootTest
@AutoConfigureMockMvc
class GatewaySeguroApplicationTests {

	@Autowired
	private MockMvc mockMvc;


	@Autowired
	private MockRestServiceServer mockRestServiceServer;

	@BeforeEach
	void reiniciarServidorSimulado() {
		mockRestServiceServer.reset();
	}

	@Autowired
	@Qualifier("restClientBuilderTeste")
	private RestClient.Builder restClientBuilderTeste;

	@Test
	void contextLoads() {
	}

	@Test
	void endpointPublicoDeveRetornar200SemJwt() throws Exception {
		mockMvc.perform(get("/api/status"))
				.andExpect(status().isOk())
				.andExpect(content().string("Gateway Seguro funcionando!"));
	}


	@Test
	void endpointPrivadoDeveRetornar401SemJwt() throws Exception {
		mockMvc.perform(get("/api/privado"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().json("""
                    {
                      "erro": "NAO_AUTENTICADO",
                      "mensagem": "Token JWT ausente ou invalido"
                    }
                    """));
	}


	@Test
	void endpointPrivadoDeveRetornar403SemPermissaoUser() throws Exception {
		mockMvc.perform(get("/api/privado")
						.with(jwt().jwt(token -> token.subject("usuario-teste"))))
				.andExpect(status().isForbidden())
				.andExpect(content().json("""
                    {
                      "erro": "ACESSO_NEGADO",
                      "mensagem": "Usuario sem permissao para acessar este recurso"
                    }
                    """));
	}



	@Test
	void endpointPrivadoDeveRetornar200ComPermissaoUser() throws Exception {
		mockMvc.perform(get("/api/privado")
						.with(jwt()
								.jwt(token -> token
										.subject("usuario-teste")
										.claim("roles", java.util.List.of("USER")))
								.authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isOk())
				.andExpect(content().string("Acesso autenticado com JWT!"));
	}


	@Test
	void endpointPedidosDeveRetornar401SemJwt() throws Exception {
		mockMvc.perform(get("/api/pedidos"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().json("""
                    {
                      "erro": "NAO_AUTENTICADO",
                      "mensagem": "Token JWT ausente ou invalido"
                    }
                    """));
	}


	@Test
	void endpointPedidosDeveRetornar403SemPermissaoUser() throws Exception {
		mockMvc.perform(get("/api/pedidos")
						.with(jwt().jwt(token -> token
								.subject("usuario-teste")
								.claim("roles", java.util.List.of("GUEST")))))
				.andExpect(status().isForbidden())
				.andExpect(content().json("""
                    {
                      "erro": "ACESSO_NEGADO",
                      "mensagem": "Usuario sem permissao para acessar este recurso"
                    }
                    """));
	}


	@Test
	void endpointPedidosDeveRetornar200ComJwtValido() throws Exception {

		mockRestServiceServer
				.expect(requestTo("http://localhost:8081/api/pedidos"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("X-Gateway-Authenticated", "true"))
				.andRespond(withSuccess(
						"""
                        [
                          {"id":1,"produto":"Notebook"},
                          {"id":2,"produto":"Mouse"},
                          {"id":3,"produto":"Teclado"}
                        ]
                        """,
						MediaType.APPLICATION_JSON
				));

		mockMvc.perform(get("/api/pedidos")
						.with(jwt()
								.jwt(token -> token.subject("usuario-teste"))
								.authorities(
										new SimpleGrantedAuthority("ROLE_USER")
								)))
				.andExpect(status().isOk())
				.andExpect(content().json("""
                    [
                      {"id":1,"produto":"Notebook"},
                      {"id":2,"produto":"Mouse"},
                      {"id":3,"produto":"Teclado"}
                    ]
                    """));

		mockRestServiceServer.verify();
	}


	@Test
	void endpointPedidosDeveRetornar502QuandoMicroservicoFalhar() throws Exception {

		mockRestServiceServer
				.expect(requestTo("http://localhost:8081/api/pedidos"))
				.andRespond(withServerError());

		mockMvc.perform(get("/api/pedidos")
						.with(jwt()
								.jwt(token -> token.subject("usuario-teste"))
								.authorities(
										new SimpleGrantedAuthority("ROLE_USER")
								)))
				.andExpect(status().isBadGateway())
				.andExpect(content().json("""
                    {
                      "erro": "FALHA_MICROSSERVICO",
                      "mensagem": "Nao foi possivel consultar os pedidos"
                    }
                    """));

		mockRestServiceServer.verify();
	}


	@TestConfiguration
	static class ConfiguracaoRestClientTeste {

		private final RestClient.Builder builder = RestClient.builder();

		private final MockRestServiceServer servidor =
				MockRestServiceServer.bindTo(builder).build();

		@Bean
		@Primary
		RestClient.Builder restClientBuilderTeste() {
			return builder;
		}

		@Bean
		MockRestServiceServer mockRestServiceServer() {
			return servidor;
		}
	}
}
