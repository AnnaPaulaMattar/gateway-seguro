package br.com.gatewayseguro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GatewaySeguroApplication {

	public static void main(String[] args) {

		SpringApplication.run(
				GatewaySeguroApplication.class, args);
	}

}
