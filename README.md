
## Segurança da comunicação entre Gateway e microsserviço

O Gateway valida o JWT antes de encaminhar requisições
ao microsserviço de pedidos.

Após a autenticação e autorização, o Gateway adiciona
o cabeçalho interno:

X-Gateway-Authenticated: true

O microsserviço verifica esse cabeçalho e rejeita
requisições que não apresentem o valor esperado.


### Decisão arquitetural para o ambiente local

O projeto utiliza duas aplicações Spring Boot:

- Gateway Seguro: HTTPS na porta 8443.
- Microsserviço de Pedidos: HTTP na porta 8081.

O Gateway é responsável pela autenticação JWT,
autorização RBAC e limitação de requisições.

O microsserviço de pedidos está vinculado ao
endereço 127.0.0.1, restringindo conexões à
interface local da máquina.

Essa configuração foi validada no Windows com
Get-NetTCPConnection, que confirmou a escuta
em 127.0.0.1:8081.

O cabeçalho X-Gateway-Authenticated é utilizado
como indicador de encaminhamento, mas não
comprova a identidade do Gateway.

Para o ambiente acadêmico local, essa limitação
é conhecida e documentada.

Para uma implantação em produção, seriam
necessários controles adicionais, incluindo
isolamento de rede e autenticação entre serviços,
preferencialmente com mTLS.

A comunicação HTTP interna utilizada neste
protótipo não deve ser considerada equivalente
a uma conexão autenticada e criptografada.


### Limitação identificada

O cabeçalho HTTP não constitui, isoladamente, um
mecanismo seguro de autenticação entre serviços.

Nos testes locais, uma requisição direta ao
microsserviço, contendo o cabeçalho falsificado,
foi aceita com HTTP 200.

Isso demonstra que o microsserviço não consegue
verificar a identidade real de quem enviou
a requisição.

### Medidas de proteção

No ambiente de desenvolvimento, o microsserviço
está configurado para escutar somente em 127.0.0.1.

Em produção, recomenda-se:

- Restringir o acesso ao microsserviço por regras
  de rede, permitindo somente o Gateway.
- Utilizar autenticação entre serviços, como mTLS.
- Não confiar em cabeçalhos fornecidos pelo cliente.
- Manter a validação JWT e a autorização no Gateway.

### Conclusão

O cabeçalho X-Gateway-Authenticated é utilizado
como um indicador interno de encaminhamento.

Ele não substitui a autenticação entre serviços
nem o isolamento da rede.

A configuração local reduz a exposição externa,
mas não impede acessos de outros processos
executados na mesma máquina.


## Proteção CSRF e gerenciamento de sessões

O Gateway Seguro utiliza uma arquitetura REST stateless,
com autenticação baseada em JWT.

No Spring Security, foram aplicadas as configurações:

- CSRF desabilitado.
- SessionCreationPolicy.STATELESS.

### Justificativa técnica

O ataque CSRF (Cross-Site Request Forgery) explora
situações em que o navegador envia automaticamente
credenciais de autenticação, como cookies de sessão,
em requisições realizadas para outros sites.

Neste projeto, a autenticação é realizada por meio
de tokens JWT enviados explicitamente no cabeçalho:

Authorization: Bearer <token>

O Gateway não utiliza cookies de sessão para
autenticar as requisições e não depende de sessões
HTTP para manter o estado de autenticação.

Por esse motivo, a proteção CSRF foi desabilitada
para os endpoints da API.

Essa decisão é adequada à arquitetura implementada,
desde que as credenciais continuem sendo enviadas
explicitamente pelo cliente e não sejam utilizadas
como cookies de autenticação enviados automaticamente
pelo navegador.

### Considerações de segurança

A configuração stateless não elimina a necessidade
de validar os tokens JWT.

O Gateway continua responsável por:

- Validar a assinatura RSA do JWT.
- Verificar a expiração do token.
- Validar o emissor autorizado.
- Aplicar as regras de autorização RBAC.
- Exigir HTTPS para a comunicação externa.

Caso a aplicação passe a utilizar autenticação
baseada em cookies ou outras credenciais enviadas
automaticamente pelo navegador, a necessidade de
proteção CSRF deverá ser reavaliada.


## Validação funcional e testes de segurança

Os testes foram executados em ambiente local,
utilizando PowerShell, curl e as aplicações
Spring Boot em execução.

### Resultados obtidos

| Teste | Resultado esperado | Resultado obtido |
|---|---|---|
| Endpoint público sem JWT | HTTP 200 | Aprovado |
| Endpoint privado sem JWT | HTTP 401 | Aprovado |
| Endpoint privado com JWT USER | HTTP 200 | Aprovado |
| Endpoint privado com JWT GUEST | HTTP 403 | Aprovado |
| Consulta de pedidos pelo Gateway | HTTP 200 | Aprovado |
| Microsserviço indisponível | HTTP 502 | Aprovado |
| Recuperação do microsserviço | HTTP 200 | Aprovado |
| Rate Limiting por IP | HTTP 429 após 30 requisições | Aprovado |
| Rate Limiting por usuário | HTTP 429 após 20 requisições | Aprovado |
| JSON de limite excedido | LIMITE_EXCEDIDO | Aprovado |
| Limpeza automática dos contadores | Remoção de contadores expirados | Aprovado |
| Cabeçalhos CSP e HSTS | Presentes na resposta HTTPS | Aprovado |

### Validação do isolamento do microsserviço

A configuração de rede foi verificada no Windows,
confirmando que o microsserviço escuta somente
em 127.0.0.1:8081.

O acesso direto sem o cabeçalho interno retornou
HTTP 403.

O acesso direto com o cabeçalho
X-Gateway-Authenticated: true retornou HTTP 200.

Esse último resultado demonstra uma limitação
conhecida: o cabeçalho pode ser falsificado por
processos que consigam acessar o microsserviço.

### Conclusão dos testes

Os testes executados confirmaram o funcionamento
dos controles implementados no protótipo,
incluindo autenticação JWT, autorização RBAC,
HTTPS, cabeçalhos de segurança, Rate Limiting,
encaminhamento de requisições e tratamento de erros.

Os resultados representam validações funcionais
em ambiente local e não constituem uma auditoria
de segurança ou certificação para produção.


## Testes automatizados

O projeto possui 9 testes automatizados desenvolvidos
com JUnit 5, Spring Boot Test, MockMvc e
MockRestServiceServer.

Os testes verificam:

- Inicialização do contexto Spring Boot.
- Acesso público sem JWT: HTTP 200.
- Acesso privado sem JWT: HTTP 401.
- Acesso privado sem a permissão USER: HTTP 403.
- Acesso privado com a permissão USER: HTTP 200.
- Acesso ao endpoint de pedidos sem JWT: HTTP 401.
- Acesso ao endpoint de pedidos sem permissão USER: HTTP 403.
- Comunicação autenticada com o microsserviço simulado: HTTP 200.
- Tratamento de falha do microsserviço: HTTP 502.

### Execução dos testes

No terminal, na raiz do projeto:

```powershell
.\mvnw.cmd test
```

### Resultado da validação

Tests run: 9, Failures: 0, Errors: 0, Skipped: 0

BUILD SUCCESS

Os testes automatizados utilizam respostas HTTP
simuladas e não dependem da execução do
microsserviço de pedidos.


## Execução do Gateway Seguro

### Pré-requisitos

- Java 17 ou superior, compatível com a versão do projeto.
- Certificado HTTPS local em formato PKCS12.
- Variável de ambiente GATEWAY_SSL_PASSWORD configurada.
- Microsserviço de pedidos em execução na porta 8081,
  para consultas reais de pedidos.

### Gerar o executável

No PowerShell, na raiz do projeto:

```powershell
.\mvnw.cmd clean package
```

### Executar o Gateway

Na mesma sessão do PowerShell, configure a senha do
certificado HTTPS, sem registrá-la no código:

```powershell
$env:GATEWAY_SSL_PASSWORD = Read-Host "Senha do certificado HTTPS"
```

Em seguida, execute:

```powershell
java -jar .\target\gateway-seguro-0.0.1-SNAPSHOT.jar
```

O Gateway ficará disponível em:

https://localhost:8443

### Verificar o funcionamento

Em outro terminal PowerShell:

```powershell
curl.exe -k -i https://localhost:8443/api/status
```

Resultado esperado: HTTP 200.

A opção -k é utilizada somente no ambiente local,
devido ao certificado HTTPS autoassinado.

Em produção, deve ser utilizado um certificado
confiável e a validação TLS não deve ser desabilitada.

### Observações

O endpoint /api/status é público.

Os endpoints /api/privado e /api/pedidos exigem
um JWT válido com a permissão USER.

A consulta real de pedidos depende do microsserviço
disponível em http://127.0.0.1:8081.

O arquivo .jar não contém a chave privada TLS nem
a senha do certificado. Esses elementos devem ser
configurados separadamente no ambiente de execução.
