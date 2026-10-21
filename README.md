# FitForge — API Gateway & BFF

Camada de interface do backend do **FitForge**: um único endereço para o frontend, com **API Gateway** (proxy reverso) e **BFF** (Backend For Frontend), feito em Spring Cloud Gateway (WebFlux).
Atividade Formativa 16 — Arquitetura de Soluções Computacionais. Integrantes em [GRUPO.md](GRUPO.md).

## Endereço publicado

**Aplicativo Web (Azure):** https://PREENCHER.azurewebsites.net

| O que ver | Caminho |
|---|---|
| Painel com status dos microsserviços | `/` |
| Visão geral (BFF) | `/api/bff/dashboard` |
| Rotas ativas do gateway | `/actuator/gateway/routes` |
| Swagger UI | `/swagger-ui.html` |

## Repositórios do projeto

| Camada | Repositório |
|---|---|
| Frontend (Module Federation) | [Valeee00/FitForge](https://github.com/Valeee00/FitForge) |
| Microsserviços | [Valeee00/FitForge-Microservicos](https://github.com/Valeee00/FitForge-Microservicos) |
| API Gateway + BFF (este) | [Valeee00/FitForge-API_Gateway](https://github.com/Valeee00/FitForge-API_Gateway) |

## Arquitetura

```
                                  ┌─> /api/treinos/**  ──> treinos-service   (fichas e exercícios)
[ FitForge (React) ] ──> GATEWAY ─┼─> /api/sessoes/**  ──> progresso-service (sessões de treino)
     1 endereço                   └─> /api/bff/**      ──> composição dos dois serviços (BFF)
```

| | Sem gateway | Com Gateway & BFF |
|---|---|---|
| Endereços que o frontend conhece | Um por microsserviço | **Um só** |
| CORS | Repetido em cada serviço | **Centralizado** no gateway |
| Rastreabilidade | Logs desconexos | **`X-Correlation-Id`** em toda requisição, repassado aos serviços |
| Tela que usa 2 serviços | Várias chamadas do navegador | **1 chamada** ao BFF, paralela no backend |
| Serviço fora do ar | Tela quebra | BFF responde com **fallback** e status `OFFLINE` |

**Stack:** Java 21 · Spring Boot 3.3.4 · Spring Cloud Gateway 2023.0.3 · WebFlux/WebClient · Actuator · SpringDoc OpenAPI.

## Endpoints

### API Gateway (proxy reverso transparente)

| Requisição no gateway | Destino | Extras |
|---|---|---|
| `/api/treinos/**` | `TREINOS_SERVICE_URL` | Retry em GET (502/503/504) |
| `/api/sessoes/**` | `PROGRESSO_SERVICE_URL` | Retry em GET (502/503/504) |

Headers devolvidos: `X-Correlation-Id`, `X-Gateway-Route`, `X-Gateway-Execution-Time-Ms`, `X-Gateway-Processed-By`.

### BFF (endpoints sob medida para as telas)

| Endpoint | Padrão | O que faz |
|---|---|---|
| `GET /api/bff/dashboard` | Agregação paralela | Fichas + sessões + estatísticas: treinos de hoje, ritmo da semana, uso de cada ficha e últimas 5 sessões |
| `GET /api/bff/treinos/{id}` | Composição | Ficha + todas as sessões dela, com total, minutos e esforço médio |
| `GET /api/bff/historico?periodo=semana\|mes` | Composição | Sessões + situação atual da ficha (excluída ou renomeada depois) |
| `GET /api/bff/status` | Health check | `/actuator/health` de cada serviço (UP/DOWN + latência) |

## Estrutura

```
src/main/java/br/fitforge/gateway/
├── filter/CorrelationIdFilter.java    # gera/propaga X-Correlation-Id
├── filter/GlobalLoggingFilter.java    # log de entrada/saída + latência
├── config/WebClientConfig.java        # clientes reativos dos 2 serviços
└── bff/  controller/ service/ dto/    # BFF (Mono.zip em paralelo, fallback)
src/main/resources/application.yml     # rotas, CORS, retry, timeouts
```

## Executar localmente

Com os microsserviços rodando (8081 e 8082) e JDK 21 + Maven:

```bash
./start-gateway.sh      # ou: mvn spring-boot:run
mvn test                # testes (sobem o gateway sem os serviços e validam fallback)
```

Abra http://localhost:8080.

## Publicação no Azure (Aplicativo Web)

1. Criar o **Aplicativo Web**: Publicar = Código · Pilha = **Java 21** · Servidor = **Java SE** · SO Linux · mesmo grupo de recursos dos microsserviços. Na aba *Implantação*, **habilitar a autenticação básica**.
2. **Configurações → Variáveis de ambiente**: `TREINOS_SERVICE_URL` e `PROGRESSO_SERVICE_URL` com as URLs dos dois microsserviços (sem `/` no final).
3. **Visão geral → Baixar perfil de publicação**.
4. No GitHub (**Settings → Secrets and variables → Actions**):
   - Secret `AZUREAPPSERVICE_PUBLISHPROFILE` = conteúdo do arquivo baixado.
   - Variable `AZURE_WEBAPP_GATEWAY` = nome do Aplicativo Web.
5. **Actions → Deploy FitForge Gateway & BFF → Run workflow**.
