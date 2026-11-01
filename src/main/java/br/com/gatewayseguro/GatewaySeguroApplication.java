package br.com.gatewayseguro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@EnableScheduling
public class GatewaySeguroApplication {

	@Bean
	public RestClient.Builder restClientBuilder() {
		return RestClient.builder();
	}

	public static void main(String[] args) {

		SpringApplication.run(
				GatewaySeguroApplication.class, args);
	}

}
