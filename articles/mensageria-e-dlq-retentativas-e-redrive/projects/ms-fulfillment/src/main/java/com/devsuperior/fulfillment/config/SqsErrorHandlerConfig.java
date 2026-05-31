package com.devsuperior.fulfillment.config;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.listener.errorhandler.ErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * Substitui o {@link SqsMessageListenerContainerFactory} default do
 * spring-cloud-aws para plugar um {@link ErrorHandler} customizado que
 * apenas loga o ApproximateReceiveCount e relanca a excecao.
 *
 * <p>O ApproximateReceiveCount vem como header da mensagem na chave
 * {@link SqsHeaders.MessageSystemAttributes#SQS_APPROXIMATE_RECEIVE_COUNT}
 * (string com o numero da tentativa atual). Quando esse valor atinge
 * o {@code maxReceiveCount=3} configurado no RedrivePolicy da
 * fulfillment-queue.fifo, o proprio SQS move a mensagem para a
 * fulfillment-dlq.fifo, sem qualquer logica extra no codigo da aplicacao.
 *
 * <p>Esse handler NAO classifica excecao (transiente vs permanente),
 * NAO altera VisibilityTimeout e NAO faz jitter. Tudo isso vive na POC
 * Plus do episodio. Aqui o objetivo e mostrar o caminho minimo
 * funcional para o leitor entender o ciclo de vida da mensagem.
 *
 * <p>O bean precisa se chamar {@code defaultSqsListenerContainerFactory}
 * para substituir o que o autoconfigure cria por padrao, esse e o nome
 * que o {@code @SqsListener} resolve quando nao se passa o atributo
 * {@code factory}.
 */
@Configuration
public class SqsErrorHandlerConfig {

    private static final Logger log = LoggerFactory.getLogger(SqsErrorHandlerConfig.class);

    @Bean
    public ErrorHandler<Object> approximateReceiveCountErrorHandler() {
        return new ErrorHandler<Object>() {
            @Override
            public void handle(Message<Object> message, Throwable throwable) {
                String receiveCount = (String) message.getHeaders()
                        .get(SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT);
                Object messageId = message.getHeaders().getId();
                // O framework empacota a excecao do listener em wrappers como
                // AsyncAdapterBlockingExecutionFailedException, AbortedException,
                // etc. Loga a mensagem da causa raiz para o leitor enxergar o
                // motivo real (ex: "Gateway de impressao indisponivel") sem
                // ruido de "Error executing action in BlockingMessageListenerAdapter".
                Throwable rootCause = unwrap(throwable);
                log.warn("Tentativa #{} de processar mensagem {} falhou: {}",
                        receiveCount != null ? receiveCount : "?",
                        messageId,
                        rootCause.getMessage());
                // Relanca a excecao original para o framework nao confirmar a
                // mensagem, ela volta para a fila apos o VisibilityTimeout
                // (default do SQS).
                if (throwable instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(throwable);
            }
        };
    }

    /**
     * Desempacota o Throwable ate a causa raiz, com guarda contra ciclo e teto
     * de 5 niveis. Sem isso o log do error handler mostra wrappers do
     * spring-cloud-aws / spring-messaging em vez da excecao real lancada pelo
     * listener.
     */
    private static Throwable unwrap(Throwable t) {
        Throwable cause = t;
        Throwable deepest = t;
        for (int i = 0; i < 5 && cause != null; i++) {
            deepest = cause;
            if (cause.getCause() == null || cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }
        return deepest;
    }

    @Bean(name = "defaultSqsListenerContainerFactory")
    public SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(
            SqsAsyncClient sqsAsyncClient,
            ErrorHandler<Object> approximateReceiveCountErrorHandler) {
        SqsMessageListenerContainerFactory<Object> factory = new SqsMessageListenerContainerFactory<>();
        factory.setSqsAsyncClient(sqsAsyncClient);
        factory.setErrorHandler(approximateReceiveCountErrorHandler);
        return factory;
    }
}
