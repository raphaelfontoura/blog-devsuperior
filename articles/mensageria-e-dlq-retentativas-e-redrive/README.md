# mensageria-e-dlq-retentativas-e-redrive

## Metadados

- Titulo: Mensageria e DLQ: Retentativas e Redrive
- Stack: Java, Spring Boot, AWS, SQS, SNS, Docker, Localstack
- Post: https://devsuperior.com.br/blog/mensageria-e-dlq-retentativas-e-redrive

## Projetos

- `projects/localstack`, infra local (SQS + SNS via LocalStack, topologia criada por init script)
- `projects/ms-ticket-ingestor` (8081), recebe POST `/api/reservations` e enfileira
- `projects/ms-reservation-handler` (8082), valida, calcula total e publica evento
- `projects/ms-notification` (8083), SseEmitter por `reservationId`
- `projects/ms-fulfillment` (8084), consumer com errorHandler, unico com DLQ

Para subir tudo e rodar o roteiro completo, consulte [`projects/README.md`](projects/README.md).
