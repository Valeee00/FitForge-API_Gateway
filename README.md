# FitForge — API Gateway & BFF

Ponto de entrada único do backend do **FitForge**: **API Gateway** (roteamento, segurança, CORS, observabilidade) e **BFF** (agregação e composição de dados para o frontend), em **Spring Cloud Gateway (WebFlux)** com **arquitetura Hexagonal**.
Projeto Arquitetura Completa — Arquitetura de Soluções Computacionais (PUCPR). Integrantes em [GRUPO.md](GRUPO.md).

## Integrantes

- Andryus Zolet
- Nicolas Medeiros
- Valentin Rojas
- Leonardo Henrique

## Vídeo de apresentação

▶️ **YouTube (não listado):** https://youtu.be/PREENCHER

## Endereços publicados (Azure)

| Componente | Endereço |
|---|---|
| **API Gateway + BFF (este)** | https://fitforge-gateway-hvaqcubhf8d0a2hc.italynorth-01.azurewebsites.net |
| Swagger UI do gateway/BFF | https://fitforge-gateway-hvaqcubhf8d0a2hc.italynorth-01.azurewebsites.net/swagger-ui.html |
| Frontend (React) | https://fitforgevalentin.z38.web.core.windows.net/ |
| treinos-service | https://fitforge-treinos-cchfd7b9dyf7cjgu.italynorth-01.azurewebsites.net |
| progresso-service | https://fitforge-progresso-gvfgfua7b0dggrht.italynorth-01.azurewebsites.net |

## Repositórios do projeto

| Camada | Repositório |
|---|---|
| Frontend (Microfrontend React) | [Valeee00/FitForge](https://github.com/Valeee00/FitForge) |
| API Gateway + BFF (este) | [Valeee00/FitForge-API_Gateway](https://github.com/Valeee00/FitForge-API_Gateway) |
| Backend / Microsserviços | [Valeee00/FitForge-Microservicos](https://github.com/Valeee00/FitForge-Microservicos) |

## O que o gateway faz

```
                                 ┌─ /api/treinos/**  ─proxy─► treinos-service   (MongoDB)
[ Frontend ] ──► API GATEWAY ────┼─ /api/sessoes/**  ─proxy─► progresso-service (Azure SQL)
                 + BFF           └─ /api/bff/**      ─agrega─► composição dos dois serviços
```

| Requisito | Como foi atendido |
|---|---|
| **Entrada centralizada** | O frontend só conhece este endereço |
| **Roteamento** | Rotas Spring Cloud Gateway em `application.yml` (`/api/treinos/**`, `/api/sessoes/**`) |
| **CRUD via proxy** | `GET/POST/PUT/DELETE` repassados aos serviços, com retry em GET (502/503/504) |
| **Agregação (BFF)** | `/api/bff/dashboard`, `/api/bff/treinos/{id}`, `/api/bff/historico` juntam dados dos dois serviços em paralelo (`Mono.zip`) com *fallback* se um cair |
| **Segurança** | Cabeçalhos de segurança, limite de requisições por minuto por IP, limite de tamanho de corpo, CORS centralizado e **API Key opcional** (`X-API-Key`, ativada por `GATEWAY_API_KEY`) |
| **Rastreabilidade** | `X-Correlation-Id` gerado/propagado a todos os serviços, log de entrada/saída com latência |
| **Documentação (Swagger)** | `/swagger-ui.html` (endpoints do BFF) e seletor com o Swagger dos dois microsserviços via `/docs/treinos` e `/docs/progresso` |

### Endpoints do BFF

| Endpoint | Padrão | O que faz |
|---|---|---|
| `GET /api/bff/dashboard` | Agregação paralela | Fichas + sessões + estatísticas: treinos de hoje, ritmo da semana, uso de cada ficha, últimas sessões |
| `GET /api/bff/treinos/{id}` | Composição | Ficha + todas as sessões dela, com total, minutos e esforço médio |
| `GET /api/bff/historico?periodo=semana\|mes` | Composição | Sessões + situação atual da ficha (excluída/renomeada) |
| `GET /api/bff/status` | Health check | `/actuator/health` de cada serviço (UP/DOWN + latência) |

## Arquitetura adotada: Hexagonal (Ports & Adapters)

```
br.fitforge.gateway
├── application/                    NÚCLEO (sem Spring)
│   ├── model/                      DTOs/registros do domínio do BFF
│   ├── exception/                  erros de negócio
│   ├── port/in/BffUseCase          o que o BFF oferece
│   ├── port/out/                   TreinosClientPort · ProgressoClientPort (o que o BFF precisa)
│   └── service/BffService          agregação, composição, fallback
├── adapter/
│   ├── in/web/                     BffController, tratamento de erros
│   └── out/http/                   WebClient → treinos-service / progresso-service
├── infrastructure/                 preocupações transversais do gateway
│   ├── security/                   API Key, rate limit, security headers
│   └── observability/              Correlation-Id, logging
└── config/                         beans, OpenAPI
```

Dependências só apontam para dentro (`adapter → application`). O `BffService` não conhece Spring nem HTTP: conversa com os serviços por **portas**, o que permite testá-lo com falsos. Isso é garantido por testes de arquitetura (ArchUnit).

## Executar localmente

Requisitos: **JDK 21** e **Maven**. Suba antes os [microsserviços](https://github.com/Valeee00/FitForge-Microservicos) (8081 e 8082).

```bash
./start-gateway.sh        # ou: mvn spring-boot:run   → http://localhost:8080
mvn test                  # testes unitários, de arquitetura e de integração
```

| Variável | Padrão | Para quê |
|---|---|---|
| `TREINOS_SERVICE_URL` | `http://localhost:8081` | Endereço do treinos-service |
| `PROGRESSO_SERVICE_URL` | `http://localhost:8082` | Endereço do progresso-service |
| `GATEWAY_API_KEY` | vazio (desligada) | Se definida, `/api/**` exige o cabeçalho `X-API-Key` |
| `GATEWAY_RATE_LIMIT` | `120` | Requisições por minuto por IP |

## Testes

- **Unitários** — `BffServiceTest`: agregação, composição, *fallback* e status com portas falsas (sem rede, sem Spring).
- **Arquitetura** — `HexagonalArchitectureTest` (ArchUnit): `onionArchitecture()`; núcleo não depende de Spring nem de adapters; controller não acessa clientes HTTP; portas são interfaces.
- **Integração** — `GatewayApplicationTests`: sobe o gateway sem os serviços e valida *fallback*, `X-Correlation-Id` e erros de proxy.

## Publicação no Azure

Aplicativo Web Linux (**Java SE 21, plano F1 gratuito**) implantado por GitHub Actions (`.github/workflows/deploy-azure-webapp.yml`). Secret `AZUREAPPSERVICE_PUBLISHPROFILE`; variable `AZURE_WEBAPP_GATEWAY`. Variáveis de ambiente no Web App: `TREINOS_SERVICE_URL` e `PROGRESSO_SERVICE_URL` (sem `/` no final).

---

# Documentação de arquitetura (Arc42)

## 1. Introdução e metas
**Visão.** Um único ponto de entrada para o frontend, escondendo os microsserviços e entregando respostas sob medida para cada tela.
**Requisitos:** roteamento e proxy de CRUD; agregação de dados de 2 serviços; segurança; documentação Swagger; resiliência a falhas dos serviços.
**Metas de qualidade:** baixo acoplamento do frontend ao backend, resiliência (fallback), rastreabilidade, testabilidade, custo zero.
**Partes interessadas:** equipe (desenvolvimento), professor (avaliação), frontend (consumidor).

## 2. Restrições
Java 21, Spring Boot 3.3.4, Spring Cloud 2023.0.3 (WebFlux, reativo); Azure App Service F1 em Italy North; repositório público; deploy por GitHub Actions; sem estado nem banco próprio.

## 3. Contexto e escopo
```
Frontend (navegador) ──HTTPS/JSON──► Gateway + BFF ──HTTP──► treinos-service   ──► MongoDB
                                                    └─HTTP─► progresso-service ──► Azure SQL
                                   (os serviços trocam eventos entre si pelo Azure Service Bus)
```
O gateway **não** acessa bancos nem o Service Bus; só HTTP. Fora do escopo: autenticação de usuários finais.

## 4. Estratégia de solução
| Meta | Decisão |
|---|---|
| Entrada única | Spring Cloud Gateway com rotas declarativas |
| Telas com 2 serviços | BFF com `Mono.zip` (chamadas em paralelo) |
| Serviço fora do ar | `opcional(...)` converte erro em `Optional.empty()` + status `OFFLINE` |
| Núcleo testável | Hexagonal: `BffService` só conhece portas |
| Segurança básica | Filtros: API Key opcional, rate limit, security headers, limite de corpo |

## 5. Visão de blocos de construção
**Nível 1:** Gateway (rotas/filtros) e BFF (núcleo + adapters).
**Nível 2:** `BffController` → `BffUseCase` → `BffService` → `TreinosClientPort`/`ProgressoClientPort` → `WebClient*Adapter`. Filtros em `infrastructure`: `CorrelationIdFilter`, `GlobalLoggingFilter`, `SecurityHeadersFilter`, `RateLimitFilter`, `ApiKeyFilter`.

## 6. Visão de tempo de execução
**Dashboard:** `GET /api/bff/dashboard` → filtros (correlation-id, segurança) → `BffService.dashboard` dispara fichas, sessões e estatísticas em paralelo → monta uma resposta única → se um serviço falha, responde 200 com dados parciais e `OFFLINE` no status.
**CRUD via proxy:** `POST /api/treinos` → rota Spring Cloud Gateway → treinos-service (com `X-Correlation-Id`) → resposta devolvida com `X-Gateway-*`.
**Ficha detalhada com treinos fora:** responde 502 (`ServicoIndisponivelException`), pois a ficha é essencial.

## 7. Visão de implantação
```
GitHub Actions ──► App Service F1 (gateway, Java SE 21, Italy North)
                        │ TREINOS_SERVICE_URL / PROGRESSO_SERVICE_URL
                        ├─► App Service F1 (treinos)    ─► MongoDB Atlas
                        └─► App Service F1 (progresso)  ─► Azure SQL (free)
Frontend: Azure Storage (site estático)  ──►  gateway
```

## 8. Conceitos transversais
- **Resiliência:** timeouts, retry em GET, fallback do BFF.
- **Segurança:** `X-API-Key` opcional, rate limit por IP (padrão 120/min), `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, limite de corpo de 1 MB.
- **CORS:** centralizado no gateway (os serviços não precisam ser abertos ao navegador).
- **Observabilidade:** `X-Correlation-Id`, logs com latência, `/actuator/health`, `/actuator/gateway/routes`.
- **Documentação:** SpringDoc (WebFlux) + agregação dos Swaggers dos serviços.

## 9. Decisões de arquitetura
| # | Decisão | Motivo |
|---|---|---|
| ADR-1 | Spring Cloud Gateway (reativo) | Proxy e filtros prontos, não bloqueia em I/O |
| ADR-2 | BFF no mesmo processo do gateway | Menos recursos no plano gratuito; um deploy |
| ADR-3 | Hexagonal no BFF | Trocar o transporte HTTP e testar sem rede |
| ADR-4 | Fallback em vez de erro | Tela continua útil com um serviço fora |
| ADR-5 | API Key opcional | Segurança ativável sem quebrar o ambiente local |

## 10. Requisitos de qualidade
| Qualidade | Cenário | Verificação |
|---|---|---|
| Resiliência | Serviço fora → dashboard responde 200 parcial | `BffServiceTest`, `GatewayApplicationTests` |
| Manutenibilidade | Núcleo sem Spring/adapters | ArchUnit |
| Rastreabilidade | Todo retorno traz `X-Correlation-Id` | `GatewayApplicationTests` |
| Desempenho | 3 chamadas em paralelo, não em série | `Mono.zip` |

## 11. Riscos e débitos técnicos
- F1 gratuito: cota de CPU diária e *cold start*.
- Rate limit em memória (uma instância); API Key única compartilhada (visível no frontend se ativada).
- Sem autenticação de usuário final (fora do escopo).

## 12. Glossário
| Termo | Significado |
|---|---|
| API Gateway | Entrada única que roteia e protege as chamadas |
| BFF | Backend For Frontend: respostas sob medida para a interface |
| Agregação / Composição | Juntar dados de vários serviços em uma resposta |
| Fallback | Resposta degradada quando uma dependência falha |
| Porta / Adapter | Interface do núcleo / implementação tecnológica |
