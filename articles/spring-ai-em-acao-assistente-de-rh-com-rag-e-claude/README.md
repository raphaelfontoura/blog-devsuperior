# spring-ai-em-acao-assistente-de-rh-com-rag-e-claude

## Metadados

- Titulo: Spring AI em ação: assistente de RH com RAG e Claude
- Stack: Java, Spring Boot, Spring AI, Claude, PgVector, Redis, Ollama

## Projetos

### hr-assistant

- Caminho: `projects/hr-assistant`
- Objetivo: Assistente de RH com RAG, ancorado no manual de politicas da Aurora, com Claude no chat e Ollama (bge-m3) nos embeddings

Pre-requisitos: JDK 25, Docker e Docker Compose, e uma chave da API da Anthropic em `ANTHROPIC_API_KEY` (veja `.env.example`).

#### Execucao local (PowerShell)

```powershell
cd articles/spring-ai-em-acao-assistente-de-rh-com-rag-e-claude/projects/hr-assistant
docker compose up -d   # Postgres/PgVector, Redis Stack e Ollama (baixa o bge-m3 na 1a vez)
$env:ANTHROPIC_API_KEY = "sua-chave-aqui"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=anthropic"
```

Com a aplicacao no ar, indexe o PDF de politicas e abra o chat em http://localhost:8080:

```powershell
curl.exe -X POST -F "file=@src/main/resources/docs/aurora_car_dealer_politicas_rh.pdf" http://localhost:8080/ingest
```

#### Testes

```powershell
cd articles/spring-ai-em-acao-assistente-de-rh-com-rag-e-claude/projects/hr-assistant
.\mvnw.cmd test
```

