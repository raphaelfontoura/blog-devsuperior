# 📨 Série Mensageria, Episódio 3: Lidando com falhas, DLQ e Retentativas

Quatro microsserviços Spring Boot conversando via **AWS SNS FIFO + SQS FIFO**, rodando localmente com **LocalStack**. O foco deste episódio é o que acontece quando um consumidor falha: o SQS retenta o consumo até `maxReceiveCount` vezes e, esgotada a paciência, redireciona a mensagem para uma **DLQ (Dead Letter Queue)**. Depois mostramos o **redrive nativo** para reprocessar tudo de volta.

## 📦 Projetos

| Projeto | Porta | Descrição |
|---------|-------|-----------|
| **ms-ticket-ingestor** | `8081` | Recebe `POST /api/reservations` e enfileira na `reservation-queue.fifo` via `SqsTemplate` |
| **ms-reservation-handler** | `8082` | Consome `reservation-queue.fifo`, valida, calcula total com taxa e publica `ReservationConfirmedEvent` no topic `ticket-events.fifo` |
| **ms-notification** | `8083` | Consome `notification-queue.fifo` (assinante do topic) e empurra confirmação via Server-Sent Events filtradas por `reservationId` |
| **ms-fulfillment** | `8084` | Consome `fulfillment-queue.fifo` (assinante do topic) com `errorHandler` customizado, único serviço com DLQ atrelada (`fulfillment-dlq.fifo`) |

## 🧭 Topologia de mensageria

- 1 topic SNS FIFO: `ticket-events.fifo`
- 3 filas SQS FIFO HT principais: `reservation-queue.fifo`, `notification-queue.fifo`, `fulfillment-queue.fifo`
- 1 DLQ FIFO: `fulfillment-dlq.fifo` (atrelada à `fulfillment-queue.fifo` com `maxReceiveCount=3`)
- 2 subscriptions SNS para SQS, ambas com `RawMessageDelivery=true` para o `@SqsListener` desserializar direto no record `ReservationConfirmedEvent`.

> A `reservation-queue.fifo` é ponto a ponto (não assina o topic). O fan-out começa quando o `ms-reservation-handler` publica em `ticket-events.fifo` após persistir a reserva.

## 🏗️ Pré-requisitos

- Java 25
- Docker rodando (Docker Desktop ou Rancher Desktop)
- `awslocal` para inspecionar a topologia (`pip install awscli-local`)
- Token do LocalStack ([crie uma conta gratuita](https://app.localstack.cloud/auth-tokens))

## 🚀 Subindo tudo junto

```bash
# 1. Exporte as variáveis
export LOCALSTACK_AUTH_TOKEN=ls_seu_token_aqui
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test

# 2. Suba o LocalStack (cria topic, filas, DLQ e subscriptions automaticamente)
cd localstack && docker-compose up -d && cd ..

# 3. Aguarde a topologia ser criada
docker logs -f localstack 2>&1 | grep -m1 "Topologia criada"

# 4. Confirme topology
awslocal --endpoint-url http://localhost:4566 sqs list-queues
awslocal --endpoint-url http://localhost:4566 sns list-topics

# 5. Suba os 4 microsserviços (cada um em um terminal)
cd ms-ticket-ingestor      && ./mvnw spring-boot:run
cd ms-reservation-handler  && ./mvnw spring-boot:run
cd ms-notification         && ./mvnw spring-boot:run
cd ms-fulfillment          && ./mvnw spring-boot:run
```

Cada serviço expõe `/actuator/health` na sua porta. Sinal de vida:

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
```

## 🧪 Caminho feliz (fluxo SSE)

Em um terminal extra, abra o stream SSE para uma reserva fictícia:

```bash
curl -N http://localhost:8083/api/notifications/stream/r-001
```

Em outro terminal, dispare a reserva:

```bash
curl -X POST http://localhost:8081/api/reservations \
  -H "Content-Type: application/json" \
  -d '{
    "reservationId": "r-001",
    "showId": "show-coldplay-2026",
    "ticketTier": "PISTA",
    "quantity": 2,
    "unitPriceUsd": 120.00,
    "buyerEmail": "ana@example.com",
    "requestedAt": "2026-05-26T18:00:00Z"
  }'
```

✅ Resposta esperada (HTTP 202):

```json
{"status":"accepted","correlationId":"...","reservationId":"r-001"}
```

No terminal do SSE você verá em poucos segundos:

```
event:reservation-confirmed
data:{"reservationId":"r-001","showId":"show-coldplay-2026", ...}
```

E nos logs do `ms-fulfillment`:

```
Liberando QR code para reserva r-001 (show show-coldplay-2026, tier PISTA)
```

## 🔥 Cenário de falha transiente (DLQ em ação)

Derrube o `ms-fulfillment` (Ctrl+C no terminal 4) e reinicie com o profile `gateway-down`:

```bash
cd ms-fulfillment
./mvnw spring-boot:run -Dspring-boot.run.profiles=gateway-down
```

Dispare outra reserva, com `reservationId` diferente:

```bash
curl -X POST http://localhost:8081/api/reservations \
  -H "Content-Type: application/json" \
  -d '{
    "reservationId": "r-002",
    "showId": "show-coldplay-2026",
    "ticketTier": "PISTA",
    "quantity": 1,
    "unitPriceUsd": 200.00,
    "buyerEmail": "bia@example.com",
    "requestedAt": "2026-05-26T18:05:00Z"
  }'
```

No log do `ms-fulfillment` você verá três tentativas antes da DLQ assumir:

```
Tentativa #1 de processar mensagem ... falhou: Gateway de impressao indisponivel
Tentativa #2 de processar mensagem ... falhou: Gateway de impressao indisponivel
Tentativa #3 de processar mensagem ... falhou: Gateway de impressao indisponivel
```

Confirme que a mensagem está na DLQ:

```bash
awslocal --endpoint-url http://localhost:4566 sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/fulfillment-dlq.fifo \
  --attribute-names ApproximateNumberOfMessages
```

Deve retornar `"ApproximateNumberOfMessages": "1"`.

## ♻️ Redrive: reprocessamento a partir da DLQ

Suba o `ms-fulfillment` de novo, agora **sem** o profile (gateway voltou):

```bash
cd ms-fulfillment
./mvnw spring-boot:run
```

E dispare o redrive nativo do SQS:

```bash
awslocal --endpoint-url http://localhost:4566 sqs start-message-move-task \
  --source-arn arn:aws:sqs:us-east-1:000000000000:fulfillment-dlq.fifo
```

Em segundos a mensagem volta para a `fulfillment-queue.fifo`, é consumida pelo `ms-fulfillment` e o QR code é liberado.

## 🔧 Subindo separado

### Só o LocalStack (para experimentar com `awslocal`)

```bash
cd localstack && docker-compose up -d
awslocal --endpoint-url http://localhost:4566 sqs list-queues
```

### Só os serviços do caminho feliz (sem DLQ em jogo)

```bash
cd ms-ticket-ingestor && ./mvnw spring-boot:run
cd ms-reservation-handler && ./mvnw spring-boot:run
cd ms-notification && ./mvnw spring-boot:run
# pule o ms-fulfillment se quiser focar só no SSE
```

## 🗄️ Verificando o banco (ms-reservation-handler)

O `ms-reservation-handler` persiste as reservas em H2. Console em [http://localhost:8082/h2-console](http://localhost:8082/h2-console):

| Campo | Valor |
|-------|-------|
| JDBC URL | `jdbc:h2:mem:reservationdb` |
| User | `sa` |
| Password | *(vazio)* |

```sql
SELECT * FROM reservations;
```

## 📝 Notas didáticas

- **Apenas 1 DLQ neste projeto**, atrelada à `fulfillment-queue.fifo`. As outras filas ficam sem DLQ de propósito: notificação SSE expirada não vale retentar, e a `reservation-queue.fifo` não tem efeito colateral externo neste hop.
- **`maxReceiveCount=3`** é didático, cabe na linha do tempo de uma demo de uns 2 minutos. Em produção o número certo depende do tempo de cada tentativa e da paciência do SLA.
- O `ms-fulfillment` **não** classifica exceção transiente vs permanente, **não** ajusta `VisibilityTimeout` e **não** faz jitter. Tudo isso fica para a POC Plus do episódio.
- O endpoint do SDK aponta para `http://localhost:4566` via `spring.cloud.aws.endpoint`. Em AWS real isso some do `application.properties` e o serviço usa o resolver default da SDK.

## 🛑 Parando tudo

```bash
# Pare os 4 mvnw spring-boot:run (Ctrl+C em cada terminal)
cd localstack && docker-compose down -v
```
