
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
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;


import br.com.gatewayseguro.ratelimit.RateLimitFilter;
import br.com.gatewayseguro.ratelimit.RateLimitService;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;




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
				.andExpect(request -> {
					String tokenInterno = request.getHeaders()
							.getFirst("X-Gateway-Token");

					org.junit.jupiter.api.Assertions.assertNotNull(tokenInterno);
					org.junit.jupiter.api.Assertions.assertTrue(
							tokenInterno.matches("\\d+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
					);
				})
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


	@Test
	void erroInesperadoDeveRetornar500SemExporDetalhesInternos() throws Exception {

		mockRestServiceServer
				.expect(requestTo("http://localhost:8081/api/pedidos"))
				.andRespond(request -> {
					throw new IllegalStateException(
							"Detalhe interno confidencial do servidor"
					);
				});

		mockMvc.perform(get("/api/pedidos")
						.with(jwt()
								.jwt(token -> token.subject("usuario-teste"))
								.authorities(
										new SimpleGrantedAuthority("ROLE_USER")
								)))
				.andExpect(status().isInternalServerError())
				.andExpect(content().json("""
                    {
                      "erro": "ERRO_INTERNO",
                      "mensagem": "Nao foi possivel processar a requisicao"
                    }
                    """))
				.andExpect(result -> {
					String resposta = result.getResponse().getContentAsString();

					org.junit.jupiter.api.Assertions.assertFalse(
							resposta.contains("Detalhe interno confidencial")
					);
				});

		mockRestServiceServer.verify();
	}




	@Test
	void endpointPrivadoDeveRetornar401ComJwtInvalido() throws Exception {

		mockMvc.perform(get("/api/privado")
						.header("Authorization", "Bearer token.invalido.assinatura"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(
						"application/json"))
				.andExpect(content().json("""
                    {
                      "erro": "NAO_AUTENTICADO",
                      "mensagem": "Token JWT ausente ou invalido"
                    }
                    """));
	}


	@Test
	void excessoDeRequisicoesDeveRetornar429() throws Exception {

		RateLimitService service =
				new RateLimitService(2, 20, 60);

		RateLimitFilter filter = new RateLimitFilter(service);

		MockHttpServletRequest request =
				new MockHttpServletRequest("GET", "/api/status");

		request.setRemoteAddr("192.168.50.10");

		MockHttpServletResponse primeira =
				new MockHttpServletResponse();

		filter.doFilter(
				request,
				primeira,
				(req, res) -> {}
		);

		assertEquals(200, primeira.getStatus());

		MockHttpServletResponse segunda =
				new MockHttpServletResponse();

		filter.doFilter(
				request,
				segunda,
				(req, res) -> {}
		);

		assertEquals(200, segunda.getStatus());

		MockHttpServletResponse terceira =
				new MockHttpServletResponse();

		filter.doFilter(
				request,
				terceira,
				(req, res) -> {}
		);

		assertEquals(429, terceira.getStatus());
		assertTrue(
				terceira.getContentType().startsWith("application/json")
		);
		assertTrue(
				terceira.getContentAsString()
						.contains("LIMITE_EXCEDIDO")
		);
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
