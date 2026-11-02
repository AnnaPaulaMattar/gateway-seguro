# Gateway Seguro para Microsserviços

Projeto desenvolvido em Java e Spring Boot como trabalho final de um curso de capacitação profissional, com foco na implementação de mecanismos de segurança para APIs REST e arquitetura de microsserviços.

O Gateway Seguro atua como ponto de entrada para as requisições, centralizando autenticação, autorização, controle de acesso, proteção contra excesso de requisições e encaminhamento seguro de chamadas para o microsserviço de pedidos.

## Objetivo

Implementar um API Gateway capaz de:

- Disponibilizar endpoints REST públicos e protegidos.
- Utilizar HTTPS para comunicação com os clientes.
- Autenticar requisições por meio de tokens JWT.
- Validar assinaturas digitais RSA utilizando RS256.
- Aplicar autorização baseada em papéis (RBAC).
- Implementar controle de requisições por IP e por usuário.
- Padronizar respostas de erros HTTP.
- Aplicar cabeçalhos de segurança.
- Encaminhar requisições autenticadas ao microsserviço de pedidos.
- Proteger a comunicação interna utilizando HMAC-SHA256.
- Impedir a reutilização de credenciais internas.

## Tecnologias utilizadas

- Java 17.
- Spring Boot.
- Spring Security.
- Spring Web MVC.
- Spring OAuth2 Resource Server.
- JWT com assinatura RS256.
- HMAC-SHA256.
- Maven e Maven Wrapper.
- JUnit 5.
- Spring Boot Test.
- MockMvc.
- MockRestServiceServer.
- PowerShell e curl para testes manuais.
- IntelliJ IDEA.

## Arquitetura

O projeto utiliza duas aplicações Spring Boot independentes:

- **Gateway Seguro:** HTTPS na porta 8443.
- **Microsserviço de Pedidos:** HTTP na porta 8081, vinculado ao endereço `127.0.0.1`.

O fluxo de comunicação é:

```text
Cliente
   |
   | HTTPS + JWT
   v
Gateway Seguro
https://localhost:8443
   |
   | Validação JWT RS256
   | Autorização RBAC
   | Rate Limiting
   | Cabeçalhos de segurança
   | Geração de credencial HMAC-SHA256
   v
Microsserviço de Pedidos
http://127.0.0.1:8081
   |
   | Validação HMAC-SHA256
   | Validação do timestamp
   | Validação do método HTTP e rota
   | Proteção contra replay
   v
Resposta JSON
```

O Gateway realiza a autenticação e autorização do cliente. O microsserviço executa uma validação adicional para confirmar a autenticidade da credencial interna.

## Endpoints

| Método | Endpoint | Autenticação |
|---|---|---|
| GET | `/api/status` | Público |
| GET | `/api/privado` | JWT com permissão USER |
| GET | `/api/pedidos` | JWT com permissão USER |

### Endpoint público

`GET /api/status`

Resposta esperada:

```text
Gateway Seguro funcionando!
```

### Endpoint privado

`GET /api/privado`

Exige um JWT válido com a permissão `USER`.

Resposta esperada:

```text
Acesso autenticado com JWT!
```

### Endpoint de pedidos

`GET /api/pedidos`

Exige autenticação JWT e autorização RBAC.

Após a validação, o Gateway encaminha a requisição ao microsserviço utilizando uma credencial interna HMAC-SHA256.

## Autenticação JWT

O Gateway utiliza tokens JWT assinados digitalmente com o algoritmo RS256.

A assinatura é validada por meio de uma chave pública RSA, disponibilizada no certificado:

```text
src/main/resources/keys/jwt-public.crt
```

A chave privada utilizada para gerar os tokens permanece fora do repositório.

O Gateway verifica:

1. A assinatura digital RSA.
2. A validade temporal do JWT.
3. O emissor autorizado.
4. As permissões necessárias para acessar os endpoints protegidos.

O emissor configurado para o ambiente de testes é:

```text
http://localhost:9000
```

A autenticação é realizada pelo cabeçalho:

```http
Authorization: Bearer <token>
```

Tokens ausentes, inválidos, expirados ou com assinatura adulterada são rejeitados com HTTP 401.

## Autorização RBAC

O Gateway utiliza autorização baseada em papéis (*Role-Based Access Control*).

Os endpoints protegidos exigem a autoridade:

```text
ROLE_USER
```

Usuários autenticados que não possuam a permissão necessária recebem HTTP 403.

Essa separação permite distinguir autenticação de autorização:

- **Autenticação:** verifica a identidade representada pelo JWT.
- **Autorização:** verifica se o usuário possui permissão para acessar determinado recurso.

## Segurança da comunicação entre Gateway e microsserviço

O Gateway valida o JWT, verifica as permissões RBAC e aplica o controle de requisições antes de encaminhar chamadas ao microsserviço de pedidos.

### Autenticação interna HMAC-SHA256

Após autenticar e autorizar a requisição, o Gateway encaminha dois cabeçalhos ao microsserviço:

```http
X-Gateway-Authenticated: true
X-Gateway-Token: <credencial assinada>
```

O cabeçalho `X-Gateway-Authenticated` isoladamente não autoriza o acesso.

O microsserviço também exige uma credencial criptográfica válida.

### Estrutura da credencial

A credencial interna utiliza o formato:

```text
timestamp.nonce.assinatura
```

Seus componentes são:

**Timestamp:** instante de geração da credencial, representado em segundos desde a época Unix.

**Nonce:** valor aleatório de 128 bits, gerado individualmente para cada credencial.

**Assinatura:** resultado do algoritmo HMAC-SHA256, calculado com uma chave secreta compartilhada entre o Gateway e o microsserviço.

A mensagem utilizada para gerar a assinatura contém:

```text
timestamp
nonce
metodo_http
rota
```

Os campos são concatenados utilizando o caractere de quebra de linha (`\n`) como separador.

Na operação implementada, a credencial é vinculada a:

```text
GET
/api/pedidos
```

Assim, uma assinatura produzida para outra operação não é aceita.

### Validação da credencial

O microsserviço realiza as seguintes verificações:

1. Confirma se `X-Gateway-Authenticated` possui o valor `true`.
2. Verifica a presença da credencial interna.
3. Confere se a credencial contém três componentes.
4. Valida o nonce de 128 bits.
5. Verifica o timestamp e a validade máxima de 30 segundos.
6. Rejeita credenciais com timestamp futuro.
7. Recalcula a assinatura HMAC-SHA256.
8. Compara a assinatura recebida com a assinatura esperada.
9. Confirma a vinculação ao método HTTP e à rota.
10. Verifica se a credencial já foi utilizada.

Requisições sem credencial válida recebem:

```http
HTTP/1.1 403 Forbidden
```

Com resposta JSON:

```json
{
  "erro": "ACESSO_NEGADO",
  "mensagem": "Credencial interna ausente ou invalida"
}
```

### Proteção contra ataques de repetição

A implementação utiliza um nonce aleatório de 128 bits para identificar cada credencial.

Após validar a assinatura, o microsserviço registra o identificador da credencial em uma estrutura `ConcurrentHashMap`.

A operação `putIfAbsent()` garante que apenas a primeira utilização seja aceita, inclusive diante de requisições simultâneas.

Uma tentativa posterior de reutilização é rejeitada, mesmo que a credencial ainda esteja dentro dos 30 segundos de validade.

Uma rotina agendada com Spring Scheduling remove periodicamente os registros expirados.

Essa implementação oferece proteção contra replay dentro de uma única instância do microsserviço.

Em ambientes distribuídos, seria necessário utilizar um armazenamento compartilhado, como Redis, para manter o controle de credenciais utilizadas entre diferentes instâncias.

### Configuração da chave secreta

A chave HMAC é compartilhada entre o Gateway e o microsserviço por meio da variável de ambiente:

```text
GATEWAY_INTERNAL_SECRET
```

A chave deve conter pelo menos 256 bits de dados aleatórios, codificados em Base64.

No arquivo `application.properties`:

```properties
gateway.internal.secret=${GATEWAY_INTERNAL_SECRET}
```

A chave secreta real não deve ser armazenada no código-fonte nem publicada no GitHub.

### Validação de segurança

Antes da implementação do HMAC-SHA256, uma requisição direta contendo apenas o cabeçalho `X-Gateway-Authenticated: true` conseguia acessar o microsserviço.

Essa vulnerabilidade foi corrigida com a exigência de uma assinatura criptográfica válida.

Posteriormente, a implementação foi aprimorada com nonce, vinculação ao método HTTP e à rota e proteção contra replay.

Resultados dos testes realizados:

| Cenário | Resultado |
|---|---|
| Acesso direto sem credencial | HTTP 403 |
| Acesso direto com cabeçalho e token falso | HTTP 403 |
| Acesso legítimo pelo Gateway com JWT válido | HTTP 200 |
| Credencial HMAC reutilizada | Rejeitada em teste unitário |
| Credencial com método HTTP diferente | Rejeitada em teste unitário |
| Credencial com rota diferente | Rejeitada em teste unitário |
| Credencial expirada | Rejeitada em teste unitário |

### Limitações e recomendações

A implementação é adequada à demonstração dos mecanismos de segurança previstos no projeto do curso de capacitação profissional.

Entretanto, ainda existem limitações:

- A comunicação interna utiliza HTTP na interface local.
- O armazenamento de nonces é local à instância do microsserviço.
- A assinatura não inclui o conteúdo do corpo da requisição.
- A segurança da autenticação interna depende do sigilo da chave compartilhada.
- O armazenamento de nonces não possui um limite máximo explícito de entradas, embora exista limpeza periódica.

Para produção, recomenda-se isolamento de rede, TLS ou mTLS entre os serviços, gerenciamento seguro e rotação de chaves, armazenamento compartilhado de nonces e mecanismos de controle de capacidade.

A implementação não deve ser considerada equivalente a uma solução completa de segurança entre serviços para produção.

## Proteção CSRF e gerenciamento de sessões

O Gateway Seguro utiliza uma arquitetura REST *stateless*, com autenticação baseada em JWT.

No Spring Security, foram aplicadas as configurações:

- CSRF desabilitado.
- `SessionCreationPolicy.STATELESS`.

### Justificativa técnica

O ataque CSRF (*Cross-Site Request Forgery*) explora situações em que o navegador envia automaticamente credenciais de autenticação, como cookies de sessão, em requisições realizadas para outros sites.

Neste projeto, a autenticação é realizada por meio de tokens JWT enviados explicitamente no cabeçalho:

```http
Authorization: Bearer <token>
```

O Gateway não utiliza cookies de sessão para autenticar as requisições e não depende de sessões HTTP para manter o estado de autenticação.

Por esse motivo, a proteção CSRF foi desabilitada para os endpoints da API.

Essa decisão é adequada à arquitetura implementada, desde que as credenciais continuem sendo enviadas explicitamente pelo cliente e não sejam utilizadas como cookies de autenticação enviados automaticamente pelo navegador.

### Considerações de segurança

A configuração *stateless* não elimina a necessidade de validar os tokens JWT.

O Gateway continua responsável por:

- Validar a assinatura RSA do JWT.
- Verificar a expiração do token.
- Validar o emissor autorizado.
- Aplicar as regras de autorização RBAC.
- Exigir HTTPS para a comunicação externa.

Caso a aplicação passe a utilizar autenticação baseada em cookies ou outras credenciais enviadas automaticamente pelo navegador, a necessidade de proteção CSRF deverá ser reavaliada.

## Rate Limiting

O Gateway implementa controle de requisições para reduzir o risco de abuso e excesso de chamadas.

Os limites configurados são:

| Critério | Limite | Janela |
|---|---|---|
| Endereço IP | 30 requisições | 60 segundos |
| Usuário autenticado | 20 requisições | 60 segundos |

Quando um limite é ultrapassado, o Gateway retorna:

```http
HTTP/1.1 429 Too Many Requests
```

A resposta utiliza JSON padronizado, incluindo o identificador:

```text
LIMITE_EXCEDIDO
```

O controle utiliza estruturas de memória com acesso concorrente e limpeza periódica de registros expirados.

### Limitações

Os contadores são mantidos em memória local.

Em ambientes com múltiplas instâncias, recomenda-se utilizar um mecanismo distribuído para compartilhar os limites entre os servidores.

## Tratamento de erros

O Gateway utiliza respostas padronizadas para situações de falha.

| Código HTTP | Situação |
|---|---|
| 401 | JWT ausente ou inválido |
| 403 | Usuário sem permissão |
| 429 | Limite de requisições excedido |
| 500 | Erro interno inesperado |
| 502 | Falha na comunicação com o microsserviço |

### HTTP 401 — Não autenticado

```json
{
  "erro": "NAO_AUTENTICADO",
  "mensagem": "Token JWT ausente ou invalido"
}
```

### HTTP 403 — Acesso negado

```json
{
  "erro": "ACESSO_NEGADO",
  "mensagem": "Usuario sem permissao para acessar este recurso"
}
```

### HTTP 502 — Falha do microsserviço

```json
{
  "erro": "FALHA_MICROSSERVICO",
  "mensagem": "Nao foi possivel consultar os pedidos"
}
```

### HTTP 500 — Erro interno

```json
{
  "erro": "ERRO_INTERNO",
  "mensagem": "Nao foi possivel processar a requisicao"
}
```

O tratamento global de exceções evita a exposição de detalhes internos sensíveis nas respostas HTTP.

## Cabeçalhos de segurança

O Gateway utiliza cabeçalhos HTTP para reforçar a segurança das respostas.

Entre os cabeçalhos observados estão:

- `Strict-Transport-Security`
- `Content-Security-Policy`
- `X-Content-Type-Options`
- `X-Frame-Options`
- `Cache-Control`
- `Pragma`
- `Expires`

A presença de HSTS e CSP foi confirmada em resposta HTTPS real do Gateway.

O HSTS orienta navegadores a utilizarem conexões HTTPS, enquanto a CSP restringe fontes de conteúdo permitidas.

Esses mecanismos complementam a autenticação e a autorização, mas não substituem a validação dos tokens.

## Validação funcional e testes de segurança

Os testes foram executados em ambiente local, utilizando PowerShell, curl, JUnit 5 e aplicações Spring Boot em execução.

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
| Rate Limiting por IP | HTTP 429 após o limite | Aprovado |
| Rate Limiting por usuário | HTTP 429 após o limite | Aprovado |
| JSON de limite excedido | LIMITE_EXCEDIDO | Aprovado |
| Limpeza automática dos contadores | Remoção de contadores expirados | Aprovado |
| Cabeçalhos CSP e HSTS | Presentes na resposta HTTPS | Aprovado |
| Acesso direto ao microsserviço sem credencial | HTTP 403 | Aprovado |
| Cabeçalho interno com token falso | HTTP 403 | Aprovado |
| Comunicação legítima com HMAC atualizado | HTTP 200 | Aprovado |

### Validação do isolamento do microsserviço

A configuração de rede foi verificada no ambiente Windows, confirmando que o microsserviço escuta somente em `127.0.0.1:8081`.

O acesso direto sem credencial interna retornou HTTP 403.

Uma tentativa de falsificação utilizando o cabeçalho `X-Gateway-Authenticated: true` e um token falso também retornou HTTP 403.

O acesso legítimo pelo Gateway, utilizando JWT válido e credencial HMAC-SHA256, retornou HTTP 200 com a lista de pedidos.

### Conclusão dos testes

Os testes confirmaram o funcionamento dos controles implementados no ambiente local, incluindo autenticação JWT, autorização RBAC, HTTPS, cabeçalhos de segurança, rate limiting, encaminhamento de requisições, autenticação interna HMAC-SHA256 e tratamento de erros.

Os resultados representam validações funcionais e testes de segurança específicos, não constituindo uma auditoria completa ou certificação para produção.

## Testes automatizados

O projeto possui **16 testes automatizados**, desenvolvidos com JUnit 5, Spring Boot Test, MockMvc e MockRestServiceServer.

Os testes verificam:

- Inicialização do contexto Spring Boot.
- Acesso público sem JWT: HTTP 200.
- Acesso privado sem JWT: HTTP 401.
- Acesso privado sem a permissão USER: HTTP 403.
- Acesso privado com a permissão USER: HTTP 200.
- Acesso ao endpoint de pedidos sem JWT: HTTP 401.
- Acesso ao endpoint de pedidos sem permissão USER: HTTP 403.
- Comunicação autenticada com o microsserviço simulado: HTTP 200.
- Presença da credencial interna no formato `timestamp.nonce.assinatura`.
- Tratamento de falha do microsserviço: HTTP 502.
- Tratamento global de exceções: HTTP 500, sem exposição de detalhes internos.
- Rejeição de JWT malformado: HTTP 401.
- Aplicação de limites de requisições por IP.
- Aplicação de limites por usuário autenticado.
- Independência dos contadores entre endereços IP.
- Expiração da janela de controle de requisições.

### Execução dos testes

No PowerShell, na raiz do projeto:

```powershell
.\mvnw.cmd clean test
```

### Resultado da validação

Resultado obtido em 02/11/2026:

```text
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Os testes de integração do Gateway utilizam respostas HTTP simuladas para o microsserviço de pedidos e não dependem de sua execução.

Os testes automatizados do microsserviço foram executados separadamente, com o seguinte resultado:

```text
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

**Total dos dois projetos: 27 testes automatizados aprovados.**

Os cenários de JWT expirado e assinatura RSA adulterada também foram validados manualmente em etapas anteriores, utilizando tokens assinados e requisições HTTPS reais ao Gateway.

## Execução do Gateway Seguro

### Pré-requisitos

- Java 17 ou superior, compatível com a versão do projeto.
- Certificado HTTPS local em formato PKCS12.
- Variável de ambiente `GATEWAY_SSL_PASSWORD` configurada.
- Variável de ambiente `GATEWAY_INTERNAL_SECRET` configurada.
- Microsserviço de pedidos em execução na porta 8081 para consultas reais.
- Certificado público RSA utilizado para validação JWT.

O Gateway e o microsserviço devem utilizar a mesma chave interna HMAC.

### Gerar o executável

No PowerShell, na raiz do projeto:

```powershell
.\mvnw.cmd clean package
```

### Executar o Gateway

Na mesma sessão do PowerShell, configure a senha do certificado HTTPS, sem registrá-la no código:

```powershell
$env:GATEWAY_SSL_PASSWORD = Read-Host "Senha do certificado HTTPS"
```

Em seguida, execute:

```powershell
java -jar .\target\gateway-seguro-0.0.1-SNAPSHOT.jar
```

O Gateway ficará disponível em:

```text
https://localhost:8443
```

### Verificar o funcionamento

Em outro terminal PowerShell:

```powershell
curl.exe -k -i https://localhost:8443/api/status
```

Resultado esperado: HTTP 200.

A opção `-k` é utilizada somente no ambiente local, devido ao certificado HTTPS autoassinado.

Em produção, deve ser utilizado um certificado confiável e a validação TLS não deve ser desabilitada.

### Observações

O endpoint `/api/status` é público.

Os endpoints `/api/privado` e `/api/pedidos` exigem um JWT válido com a permissão USER.

A consulta real de pedidos depende do microsserviço disponível em:

```text
http://127.0.0.1:8081
```

O certificado público RSA pode ser incluído no projeto para validação de assinaturas.

A chave privada utilizada para assinar os JWTs, a chave secreta HMAC e a senha do certificado HTTPS devem permanecer fora do repositório.

## Exemplos de testes de segurança com curl

Os exemplos abaixo foram preparados para o PowerShell no Windows, com o Gateway em execução na porta 8443.

A opção `-k` permite utilizar o certificado HTTPS autoassinado apenas no ambiente de desenvolvimento.

### 1. Endpoint público sem JWT

```powershell
curl.exe -k -i https://localhost:8443/api/status
```

Resultado esperado: HTTP 200.

### 2. Endpoint privado sem JWT

```powershell
curl.exe -k -i https://localhost:8443/api/privado
```

Resultado esperado: HTTP 401.

### 3. Endpoint privado com JWT válido

Primeiro, gere um JWT válido com o projeto local `jwt-testes`.

No PowerShell:

```powershell
$token = Read-Host "Cole o JWT valido"
```

Em seguida:

```powershell
curl.exe -k -i -H "Authorization: Bearer $token" https://localhost:8443/api/privado
```

Resultado esperado: HTTP 200.

### 4. Endpoint privado com JWT inválido

```powershell
curl.exe -k -i -H "Authorization: Bearer token.invalido.assinatura" https://localhost:8443/api/privado
```

Resultado esperado: HTTP 401.

Resposta JSON:

```json
{
  "erro": "NAO_AUTENTICADO",
  "mensagem": "Token JWT ausente ou invalido"
}
```

### 5. JWT expirado

No projeto `jwt-testes`, gere temporariamente um JWT com a data de expiração no passado.

Armazene o token:

```powershell
$tokenExpirado = Read-Host "Cole o JWT expirado"
```

Execute:

```powershell
curl.exe -k -i -H "Authorization: Bearer $tokenExpirado" https://localhost:8443/api/privado
```

Resultado esperado: HTTP 401.

Após o teste, restaure a validade original do gerador.

### 6. JWT com assinatura RSA adulterada

Utilize um JWT válido armazenado na variável `$token`.

No PowerShell:

```powershell
$partes = $token.Split('.')
$assinatura = $partes[2]
$primeiro = $assinatura.Substring(0, 1)
$novoPrimeiro = if ($primeiro -eq 'A') { 'B' } else { 'A' }
$partes[2] = $novoPrimeiro + $assinatura.Substring(1)
$tokenAdulterado = $partes -join '.'
```

Execute:

```powershell
curl.exe -k -i -H "Authorization: Bearer $tokenAdulterado" https://localhost:8443/api/privado
```

Resultado esperado: HTTP 401.

O token original, sem alterações, deve retornar HTTP 200.

### 7. Autorização RBAC

Gere um JWT válido contendo a permissão GUEST, sem a permissão USER.

```powershell
$tokenGuest = Read-Host "Cole o JWT com permissao GUEST"
```

Execute:

```powershell
curl.exe -k -i -H "Authorization: Bearer $tokenGuest" https://localhost:8443/api/privado
```

Resultado esperado: HTTP 403.

### 8. Consulta de pedidos pelo Gateway

Com o microsserviço de pedidos em execução:

```powershell
curl.exe -k -i -H "Authorization: Bearer $token" https://localhost:8443/api/pedidos
```

Resultado esperado: HTTP 200.

A resposta contém os pedidos fictícios cadastrados no microsserviço.

### 9. Rate Limiting por IP

Em um terminal PowerShell, execute:

```powershell
1..35 | ForEach-Object {
    curl.exe -k -s -o NUL -w "%{http_code}`n" https://localhost:8443/api/status
}
```

O limite configurado é de 30 requisições por IP em uma janela de 60 segundos.

Ao ultrapassar o limite, o Gateway deve retornar HTTP 429.

A contagem depende das requisições já realizadas dentro da janela atual.

### 10. Rate Limiting por usuário

Com um JWT válido:

```powershell
1..25 | ForEach-Object {
    curl.exe -k -s -o NUL -w "%{http_code}`n" -H "Authorization: Bearer $token" https://localhost:8443/api/privado
}
```

O limite configurado é de 20 requisições por usuário em uma janela de 60 segundos.

Ao ultrapassar o limite, o Gateway deve retornar HTTP 429.

O limite por IP também continua sendo aplicado.

### 11. Acesso direto ao microsserviço sem credencial

```powershell
curl.exe -i http://127.0.0.1:8081/api/pedidos
```

Resultado esperado e obtido: HTTP 403.

### 12. Tentativa de falsificação da credencial interna

```powershell
curl.exe -i `
  -H "X-Gateway-Authenticated: true" `
  -H "X-Gateway-Token: token.falso.assinatura" `
  http://127.0.0.1:8081/api/pedidos
```

Resultado esperado e obtido: HTTP 403.

Esse teste demonstra que o microsserviço não confia exclusivamente no cabeçalho de identificação.

### Observações

Os tokens JWT, as chaves privadas e as senhas não devem ser publicados no repositório.

Os testes de rate limiting devem ser realizados considerando os contadores da janela atual.

O mecanismo de rate limiting utiliza memória local, sendo adequado ao ambiente demonstrativo do curso de capacitação profissional, mas não substitui uma solução distribuída para produção.

## Considerações finais

O Gateway Seguro demonstra a aplicação integrada de diferentes mecanismos de segurança em APIs REST e microsserviços.

A implementação contempla autenticação JWT com assinatura RSA, autorização RBAC, comunicação externa HTTPS, cabeçalhos de segurança, rate limiting, tratamento padronizado de erros e autenticação interna HMAC-SHA256 com proteção contra replay.

A validação realizada em 02/11/2026 apresentou:

- **16 testes automatizados aprovados no Gateway Seguro.**
- **11 testes automatizados aprovados no Microsserviço de Pedidos.**
- **27 testes automatizados aprovados no total.**
- Comunicação autenticada entre os serviços com HTTP 200.
- Bloqueio de acesso direto sem credencial com HTTP 403.
- Bloqueio de tentativa de falsificação da credencial interna com HTTP 403.

O projeto foi desenvolvido como atividade final de um curso de capacitação profissional e demonstra práticas importantes de segurança de APIs.

Embora os resultados comprovem o funcionamento dos mecanismos testados no ambiente local, a utilização em produção exigiria medidas adicionais de infraestrutura, gerenciamento de segredos, observabilidade, escalabilidade e segurança distribuída.