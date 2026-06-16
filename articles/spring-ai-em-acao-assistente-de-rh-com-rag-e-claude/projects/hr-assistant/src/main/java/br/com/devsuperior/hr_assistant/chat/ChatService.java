package br.com.devsuperior.hr_assistant.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;

    public ChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public Flux<String> stream(String message, String conversationId) {
        logger.info("[conversationId={}] Iniciando stream de chat (mensagem com {} caracteres)",
                conversationId, message.length());
        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .doOnComplete(() -> logger.info("[conversationId={}] Stream de chat concluido com sucesso",
                        conversationId))
                .doOnError(error -> logger.error("[conversationId={}] Erro durante o stream de chat: {}",
                        conversationId, error.getMessage(), error));
    }
}