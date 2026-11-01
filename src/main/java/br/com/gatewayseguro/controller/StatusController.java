
package br.com.gatewayseguro.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StatusController {

    @GetMapping("/status")
    public String verificarStatus() {
        return "Gateway Seguro funcionando!";
    }

    @GetMapping("/privado")
    public String verificarAcessoPrivado() {
        return "Acesso autenticado com JWT!";
    }
}
