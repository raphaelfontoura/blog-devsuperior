package br.com.devsuperior.hr_assistant.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import redis.clients.jedis.RedisClient;

/**
 * Valida que o bean RedisChatMemoryRepository (definido explicitamente em
 * ChatClientConfig) e construido corretamente e consegue persistir/recuperar
 * mensagens no Redis local. Reproduz exatamente a construcao do bean.
 */
class RedisChatMemoryRepositoryTest {

    private RedisChatMemoryRepository buildRepository() {
        return RedisChatMemoryRepository.builder()
                .jedisClient(RedisClient.create("localhost", 6379))
                .initializeSchema(true)
                .timeToLive(Duration.ofMinutes(30))
                .build();
    }

    @Test
    void shouldSaveAndRetrieveMessages() {
        RedisChatMemoryRepository repository = buildRepository();
        String conversationId = "test-" + UUID.randomUUID();

        List<Message> messages = List.of(
                new UserMessage("Qual o horario de almoco?"),
                new AssistantMessage("O intervalo de almoco e de 1 hora."));

        repository.saveAll(conversationId, messages);

        List<Message> retrieved = repository.findByConversationId(conversationId);

        assertThat(retrieved).hasSize(2);
        assertThat(retrieved.get(0).getText()).isEqualTo("Qual o horario de almoco?");
        assertThat(retrieved.get(1).getText()).isEqualTo("O intervalo de almoco e de 1 hora.");

        repository.deleteByConversationId(conversationId);
        assertThat(repository.findByConversationId(conversationId)).isEmpty();
    }
}
