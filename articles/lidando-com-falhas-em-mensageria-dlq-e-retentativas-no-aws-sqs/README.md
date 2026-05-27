# lidando-com-falhas-em-mensageria-dlq-e-retentativas-no-aws-sqs

## Metadados

- Titulo: Lidando com falhas em mensageria, DLQ e Retentativas no AWS SQS
- Stack: Java, Spring Boot, AWS, SQS, SNS, Docker, Localstack

## Projetos

### localstack

- Caminho: `projects/localstack`
- Objetivo: Infra local para simular AWS SQS e SNS (3 filas FIFO HT principais + 1 DLQ FIFO + 1 topic FIFO)

#### Execucao local

```bash
cd articles/lidando-com-falhas-em-mensageria-dlq-e-retentativas-no-aws-sqs/projects/localstack
docker-compose up -d
```

#### Testes

```bash
cd articles/lidando-com-falhas-em-mensageria-dlq-e-retentativas-no-aws-sqs/projects/localstack
docker logs -f localstack | grep -m1 "Topologia criada"
```

### ms-ticket-ingestor

- Caminho: `projects/ms-ticket-ingestor`
- Objetivo: Servico que recebe POST /api/reservations e enfileira na reservation-queue.fifo

#### Execucao local

```bash
cd articles/lidando-com-falhas-em-mensageria-dlq-e-retentativas-no-aws-sqs/projects/ms-ticket-ingestor
./mvnw spring-boot:run
```

#### Testes

```bash
cd articles/lidando-com-falhas-em-mensageria-dlq-e-retentativas-no-aws-sqs/projects/ms-ticket-ingestor
./mvnw test
```

### ms-reservation-handler

- Caminho: `projects/ms-reservation-handler`
- Objetivo: Consumer da reservation-queue, valida, calcula total com taxa e publica ReservationConfirmedEvent no SNS

#### Execucao local

```bash
cd articles/lidando-com-falhas-em-mensageria-dlq-e-retentativas-no-aws-sqs/projects/ms-reservation-handler
./mvnw spring-boot:run
```

#### Testes

```bash
cd articles/lidando-com-falhas-em-mensageria-dlq-e-retentativas-no-aws-sqs/projects/ms-reservation-handler
./mvnw test
```

### ms-notification

- Caminho: `projects/ms-notification`
- Objetivo: Consumer da notification-queue, mantem SseEmitter por reservationId para confirmacao em tempo real

#### Execucao local

```bash
cd articles/lidando-com-falhas-em-mensageria-dlq-e-retentativas-no-aws-sqs/projects/ms-notification
./mvnw spring-boot:run
```

#### Testes

```bash
cd articles/lidando-com-falhas-em-mensageria-dlq-e-retentativas-no-aws-sqs/projects/ms-notification
./mvnw test
```

### ms-fulfillment

- Caminho: `projects/ms-fulfillment`
- Objetivo: Consumer da fulfillment-queue com errorHandler customizado, simula gateway de impressao via profile Spring gateway-down; unico consumer com DLQ

#### Execucao local

```bash
cd articles/lidando-com-falhas-em-mensageria-dlq-e-retentativas-no-aws-sqs/projects/ms-fulfillment
./mvnw spring-boot:run
```

#### Testes

```bash
cd articles/lidando-com-falhas-em-mensageria-dlq-e-retentativas-no-aws-sqs/projects/ms-fulfillment
./mvnw test
```
