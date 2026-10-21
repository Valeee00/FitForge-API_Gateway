# API Gateway & BFF — Atividade Formativa 16

> **Disciplina:** Arquitetura de Soluções — Prof. Ricardo Vieira
> Camada de interface do backend: **API Gateway (proxy reverso)** + **BFF (Backend For Frontend)** com Spring Cloud Gateway (WebFlux).
> Integrantes: ver [GRUPO.md](GRUPO.md).

## 🌐 Endereços publicados no Azure

| Recurso | URL |
|---|---|
| **Gateway & BFF (Azure Web App)** | https://NOME-DO-SEU-WEBAPP.azurewebsites.net  ← *substituir* |
| Status dos microsserviços | https://NOME-DO-SEU-WEBAPP.azurewebsites.net/api/bff/status |
| Rotas ativas | https://NOME-DO-SEU-WEBAPP.azurewebsites.net/actuator/gateway/routes |
| Swagger UI | https://NOME-DO-SEU-WEBAPP.azurewebsites.net/swagger-ui.html |
| Frontend (Azure Static Web Apps / Storage) | https://ENDERECO-DO-FRONTEND  ← *substituir* |

---

## 🎯 Antes × depois

```
ANTES (acesso direto)                    DEPOIS (Gateway + BFF)
                                                                    ┌─> /api/veiculos/** ─> veiculos-service
[ Frontend ] ──> veiculos-service       [ Frontend ] ──> [ GATEWAY ]─┼─> /api/leiloes/**  ─> leilao-service
[ Frontend ] ──> leilao-service                          (1 endereço) └─> /api/bff/**      ─> composição local
```

| | Sem gateway | Com Gateway & BFF |
|---|---|---|
| Endereços que o frontend conhece | Um por microsserviço | **Um só** |
| CORS | Repetido em cada serviço | **Centralizado** no gateway |
| Rastreabilidade | Logs desconexos | **`X-Correlation-Id`** em toda requisição |
| Tela que precisa de 2 serviços | Várias chamadas do navegador | **1 chamada** ao BFF (paralela no backend) |
| Serviço fora do ar | Tela quebra | BFF responde com **fallback** |

## 🛠️ Stack

Java 21 · Spring Boot 3.3.4 · Spring Cloud Gateway 2023.0.3 · WebFlux/WebClient · Actuator · SpringDoc OpenAPI 2.6.0

## 📁 Estrutura

```
src/main/java/br/grupo/gateway/
├── GatewayApplication.java
├── filter/
│   ├── CorrelationIdFilter.java     # gera/propaga X-Correlation-Id (rotas e BFF)
│   └── GlobalLoggingFilter.java     # log de entrada/saída + headers de latência
├── config/
│   ├── WebClientConfig.java         # clientes reativos dos microsserviços
│   └── HomeController.java          # "/" -> painel index.html
└── bff/
    ├── controller/BffController.java
    ├── service/BffService.java      # agregação (Mono.zip) e composição (flatMap)
    └── dto/                         # contratos dos serviços + respostas do BFF
src/main/resources/
├── application.yml                  # rotas, CORS, retry, timeouts
└── static/index.html                # painel com status ao vivo
```

## 🛣️ Endpoints

### API Gateway — proxy reverso transparente

| Requisição no gateway | Destino | Observação |
|---|---|---|
| `/api/veiculos/**` (GET, POST, PUT, DELETE) | `VEICULOS_SERVICE_URL` | Retry automático em GET (502/503/504) |
| `/api/leiloes/**` (GET, POST, PATCH) | `LEILAO_SERVICE_URL` | Retry automático em GET (502/503/504) |

Headers devolvidos: `X-Correlation-Id`, `X-Gateway-Route`, `X-Gateway-Execution-Time-Ms`, `X-Gateway-Processed-By`.

### BFF — endpoints sob medida para as telas

| Endpoint | Padrão | O que faz |
|---|---|---|
| `GET /api/bff/dashboard` | Agregação paralela | 3 chamadas simultâneas (veículos, estatísticas, lotes) → totais, valor do catálogo, veículos por tipo, lotes em destaque, status dos serviços |
| `GET /api/bff/leiloes/{id}` | Composição | Lote → veículo do catálogo; calcula valorização (%), líder atual e próximo lance mínimo (+R$ 500) |
| `GET /api/bff/veiculos/{id}` | Agregação + filtro | Ficha do veículo + todos os lotes em que ele aparece, se está em leilão ativo e maior lance |
| `GET /api/bff/status` | Health check | Consulta `/actuator/health` de cada serviço (UP/DOWN + latência) |

## 🚀 Executar localmente

Pré-requisitos: JDK 21 e Maven, com os microsserviços rodando em `:8081` (veículos) e `:8082` (leilão).

```bash
./start-gateway.sh        # Linux/macOS
start-gateway.bat         # Windows
# ou
mvn spring-boot:run
```

Abra http://localhost:8080 (painel), http://localhost:8080/swagger-ui.html ou:

```bash
curl -i http://localhost:8080/api/veiculos
curl -i http://localhost:8080/api/bff/dashboard
curl -i http://localhost:8080/api/bff/leiloes/1
```

Testes automatizados (sobem o gateway sem os microsserviços e validam fallback, status DOWN e Correlation-Id):

```bash
mvn test
```

## ☁️ Publicar no Azure Web App

1. **Criar o Web App** (portal Azure → *Aplicativos Web* → *Criar*)
   - Grupo de recursos: **o mesmo** do frontend e dos microsserviços
   - Publicar: **Código** · Pilha de runtime: **Java 21** · Pilha do servidor Java: **Java SE (Embedded Web Server)**
   - SO: Linux · Região: Brazil South · Plano: Free F1 ou Basic B1
   - *Revisar + criar*
2. **Variáveis de ambiente** (Web App → *Configurações* → *Variáveis de ambiente / Configurações do aplicativo*):

   | Nome | Valor |
   |---|---|
   | `VEICULOS_SERVICE_URL` | `https://<webapp-veiculos>.azurewebsites.net` |
   | `LEILAO_SERVICE_URL` | `https://<webapp-leilao>.azurewebsites.net` |
   | `CORS_ORIGINS` *(opcional)* | origens extras, separadas por vírgula |

   Salvar e reiniciar o Web App.
3. **Habilitar a autenticação básica** (Web App → *Configurações* → *Configuração* → *Configurações gerais*) → ligar **SCM Basic Auth Publishing Credentials** → *Salvar*. Sem isso o perfil de publicação não funciona.
4. **Baixar o perfil de publicação** (*Visão geral* → *Baixar perfil de publicação*), abrir o `.PublishSettings` no Bloco de Notas e copiar todo o XML.
5. **Cadastrar o segredo no GitHub** → *Settings* → *Secrets and variables* → *Actions* → *New repository secret*
   - Name: `AZUREAPPSERVICE_PUBLISHPROFILE`
   - Secret: o XML copiado
6. Em `.github/workflows/deploy-azure-webapp.yml`, trocar `NOME-DO-SEU-WEBAPP` pelo nome do Web App.
7. `git push` na branch `main` → aba **Actions** mostra build + deploy. Depois, atualizar as URLs no topo deste README.

### Conectar o frontend (microfrontends) ao gateway

Apontar todas as URLs de API do frontend para o gateway, por exemplo:

```
VITE_VEICULOS_API_URL=https://NOME-DO-SEU-WEBAPP.azurewebsites.net
VITE_LEILAO_API_URL=https://NOME-DO-SEU-WEBAPP.azurewebsites.net
```

Rebuild e republicação do frontend: a partir daí o navegador conversa **somente** com o gateway.

---

Projeto baseado no exemplo da disciplina [AULA_ARQSOL_GATEWAY](https://github.com/ricardo-vieira/AULA_ARQSOL_GATEWAY), consumindo os microsserviços de [AULA_ARQSOL_MICROSSERVICOS](https://github.com/ricardo-vieira/AULA_ARQSOL_MICROSSERVICOS).
