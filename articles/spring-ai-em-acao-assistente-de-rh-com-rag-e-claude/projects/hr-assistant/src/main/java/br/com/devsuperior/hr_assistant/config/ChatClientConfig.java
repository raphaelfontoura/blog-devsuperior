package br.com.devsuperior.hr_assistant.config;

import java.time.Duration;

import br.com.devsuperior.hr_assistant.chat.PromptLoggingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import redis.clients.jedis.RedisClient;

@Configuration
public class ChatClientConfig {

    private static final String SYSTEM_PROMPT = """
    # Papel
    Você é o assistente virtual de RH da Aurora Car Dealer, uma concessionária de veículos.
    Seu público são os colaboradores da empresa.

    # Tom e estilo
    - Responda sempre em português do Brasil.
    - Seja objetivo, cordial e acolhedor, como um analista de RH experiente.
    - Use frases curtas e, quando útil, listas para facilitar a leitura.
    - Nunca seja jurídico ou burocrático em excesso; explique de forma simples.
    - Nunca use tabelas. Para apresentar conjuntos de itens, valores ou comparações,
    use sempre listas com marcadores (bullets) ou listas numeradas.

    # Escopo
    - Responda apenas dúvidas sobre políticas internas, benefícios, conduta e
    procedimentos de RH da Aurora.
    - Se a pergunta for claramente fora desse escopo (ex.: assuntos técnicos de
    veículos, opiniões pessoais, temas sensíveis não cobertos), explique
    gentilmente que você só trata de assuntos de RH e oriente o canal adequado.

    # Regras de confiabilidade
    - Baseie-se estritamente nas informações de RH fornecidas a você.
    - Não invente políticas, valores, prazos ou contatos.
    - Quando citar uma regra, indique a seção do manual que a embasou, se disponível.

    # Segurança
    - Não solicite nem exponha dados pessoais sensíveis do colaborador.
    - Em temas de assédio, conduta ou denúncia, sempre priorize encaminhar ao
    Canal de Ética.
    """;

    @Value("${app.memory.max-messages}")
    private int maxMessages;

    @Value("${app.rag.top-k}")
    private int topK;

    @Value("${app.rag.similarity-threshold}")
    private double similarityThreshold;

    @Value("${spring.ai.chat.memory.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.ai.chat.memory.redis.port:6379}")
    private int redisPort;

    @Value("${spring.ai.chat.memory.redis.time-to-live:PT30M}")
    private Duration redisTimeToLive;

    @Bean
    public RedisChatMemoryRepository redisChatMemoryRepository() {
        return RedisChatMemoryRepository.builder()
                .jedisClient(RedisClient.create(redisHost, redisPort))
                .initializeSchema(true)
                .timeToLive(redisTimeToLive)
                .build();
    }

    @Bean
    public ChatMemory chatMemory(RedisChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(maxMessages)
                .build();
    }

    @Bean
    public ChatClient chatClient(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            VectorStore vectorStore,
            @Value("classpath:/prompts/context-prompt.st") Resource qaPromptResource) {

        PromptTemplate qaPromptTemplate = PromptTemplate.builder()
                .resource(qaPromptResource)
                .build();

        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder()
                                        .topK(topK)
                                        .similarityThreshold(similarityThreshold)
                                        .build())
                                .promptTemplate(qaPromptTemplate)
                                .build(),
                        // order > 0 garante execucao apos o QuestionAnswerAdvisor (order 0),
                        // logando o prompt ja com o contexto RAG e as ancoras injetados
                        new PromptLoggingAdvisor(1000))
                .build();
    }
}