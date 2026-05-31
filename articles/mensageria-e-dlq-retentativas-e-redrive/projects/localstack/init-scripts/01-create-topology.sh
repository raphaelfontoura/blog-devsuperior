#!/bin/bash
# ============================================================================
# Topologia local SQS + SNS no LocalStack para o artigo "Lidando com falhas".
# Cria:
#   1 topico SNS FIFO  : ticket-events.fifo
#   3 filas SQS FIFO HT: reservation-queue.fifo, notification-queue.fifo, fulfillment-queue.fifo
#   1 fila DLQ FIFO HT : fulfillment-dlq.fifo
#   1 RedrivePolicy    : fulfillment-queue.fifo -> fulfillment-dlq.fifo (maxReceiveCount=3)
#   2 subscriptions    : ticket-events.fifo -> notification-queue.fifo
#                        ticket-events.fifo -> fulfillment-queue.fifo
#
# A reservation-queue.fifo NAO assina o topic, e ponto a ponto
# (ingestor -> reservation-handler). O fan-out comeca quando o
# reservation-handler publica no SNS apos persistir a reserva.
#
# Notification queue ficou SEM DLQ de proposito, evento de notificacao
# expirado nao vale retentar, descartar e melhor que atrasar a UX.
# Reservation queue tambem fica sem DLQ porque ainda nao tem efeito
# colateral externo nesse hop.
#
# O script e idempotente, rodar duas vezes nao quebra nada.
# ============================================================================

set -e

REGION="us-east-1"
ACCOUNT_ID="000000000000"

echo "[topology] Criando topologia local no LocalStack..."

# ----------------------------------------------------------------------------
# 1. Topico SNS FIFO com ContentBasedDeduplication=true
# ----------------------------------------------------------------------------
echo "[topology] Criando topico SNS FIFO ticket-events.fifo"
TOPIC_ARN=$(awslocal sns create-topic \
    --name ticket-events.fifo \
    --attributes FifoTopic=true,ContentBasedDeduplication=true \
    --region "$REGION" \
    --output text \
    --query 'TopicArn')

echo "[topology] Topic ARN: $TOPIC_ARN"

# ----------------------------------------------------------------------------
# 2. Filas SQS FIFO em High Throughput Mode
#    DeduplicationScope=messageGroup + FifoThroughputLimit=perMessageGroupId
#    ContentBasedDeduplication=true espelha o atributo do topico
# ----------------------------------------------------------------------------
create_fifo_queue() {
    local QUEUE_NAME="$1"
    echo "[topology] Criando fila $QUEUE_NAME"
    awslocal sqs create-queue \
        --queue-name "$QUEUE_NAME" \
        --attributes '{
            "FifoQueue": "true",
            "ContentBasedDeduplication": "true",
            "DeduplicationScope": "messageGroup",
            "FifoThroughputLimit": "perMessageGroupId",
            "VisibilityTimeout": "30",
            "ReceiveMessageWaitTimeSeconds": "20"
        }' \
        --region "$REGION" > /dev/null
}

create_fifo_queue "reservation-queue.fifo"
create_fifo_queue "notification-queue.fifo"
create_fifo_queue "fulfillment-queue.fifo"
create_fifo_queue "fulfillment-dlq.fifo"

# ----------------------------------------------------------------------------
# 3. RedrivePolicy na fulfillment-queue.fifo apontando para a DLQ
#    maxReceiveCount=3, valor didatico que cabe nos 3 ciclos do video
# ----------------------------------------------------------------------------
DLQ_ARN="arn:aws:sqs:${REGION}:${ACCOUNT_ID}:fulfillment-dlq.fifo"
FULFILLMENT_QUEUE_URL="http://localhost:4566/${ACCOUNT_ID}/fulfillment-queue.fifo"

echo "[topology] Configurando RedrivePolicy maxReceiveCount=3 na fulfillment-queue.fifo"
awslocal sqs set-queue-attributes \
    --queue-url "$FULFILLMENT_QUEUE_URL" \
    --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}" \
    --region "$REGION" > /dev/null

# ----------------------------------------------------------------------------
# 4. Subscriptions SNS FIFO -> SQS FIFO com RawMessageDelivery=true
#    Sem o envelope SNS o consumer desserializa direto em
#    ReservationConfirmedEvent.
# ----------------------------------------------------------------------------
subscribe_queue() {
    local QUEUE_NAME="$1"
    local QUEUE_ARN="arn:aws:sqs:${REGION}:${ACCOUNT_ID}:${QUEUE_NAME}"
    echo "[topology] Inscrevendo $QUEUE_NAME no topico ticket-events.fifo (RawMessageDelivery=true)"
    awslocal sns subscribe \
        --topic-arn "$TOPIC_ARN" \
        --protocol sqs \
        --notification-endpoint "$QUEUE_ARN" \
        --attributes 'RawMessageDelivery=true' \
        --region "$REGION" > /dev/null
}

subscribe_queue "notification-queue.fifo"
subscribe_queue "fulfillment-queue.fifo"

# ----------------------------------------------------------------------------
# Resumo final, pego pelo README via grep
# ----------------------------------------------------------------------------
echo ""
echo "================================================================"
echo "Topologia criada: 3 filas + 1 DLQ + 1 topic + 2 subscriptions"
echo "================================================================"
