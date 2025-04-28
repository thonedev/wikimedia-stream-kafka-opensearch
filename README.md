# Wikimedia Kafka to OpenSearch Pipeline

Este projeto cria um pipeline que consome eventos do Wikimedia via Kafka e os armazena em um cluster OpenSearch.

## Tecnologias Utilizadas

- **Java 21**
- **Spring Boot 3.4.4**
- **Apache Kafka**
- **OpenSearch**
- **Docker / Docker Compose**

## Estrutura do Projeto

- `kafka-producer-wikmedia/`
  - Produtor Kafka que consome eventos do stream do Wikimedia e publica no tópico `wikimedia.recentchange`.
- `kafka-consumer-opensearch/`
  - Consumidor Kafka que consome eventos do tópico e indexa os dados no OpenSearch.
- `docker-compose.yml`
  - Define a infraestrutura necessária com os containers do Kafka, Zookeeper, OpenSearch e OpenSearch Dashboards.

## Requisitos

- Docker e Docker Compose instalados
- Java 21 instalado
- Maven instalado

## Subindo a Infraestrutura

```bash
docker-compose up -d
```

Containers iniciados:
- Kafka (porta 9092)
- Zookeeper (porta 2181)
- OpenSearch (porta 9200)
- OpenSearch Dashboards (porta 5601)
- Kafka UI (porta 9080)

## Executando o Projeto

1. **Produtor**

   Publica eventos do Wikimedia no Kafka:

   ```bash
   cd kafka-producer-wikmedia
   ./mvnw clean compile exec:java -Dexec.mainClass="org.example.ProducerDemo"
   ```

2. **Consumidor**

   Consome eventos e envia para o OpenSearch:

   ```bash
   cd kafka-consumer-opensearch
   ./mvnw clean compile exec:java -Dexec.mainClass="org.example.OpenSearchConsumer"
   ```

## Observando os Dados

- Acesse o **Kafka UI**: [http://localhost:9080](http://localhost:9080)
- Acesse o **OpenSearch Dashboards**: [http://localhost:5601](http://localhost:5601)

## Principais Tópicos Kafka

- `wikimedia.recentchange`

## Observações

- As mensagens consumidas são inseridas em lote (bulk insert) no OpenSearch para otimizar a performance.
- IDs únicos para cada evento são extraídos do campo `meta.id` do JSON do Wikimedia.

## Contato

Projeto inspirado em tutoriais de streaming de dados. Adaptado e mantido por [thonedev](https://github.com/thonedev).



## Contribuição
Contribuições são bem-vindas! Sinta-se à vontade para abrir issues ou pull requests.

---

Feito com ❤️ por [Thone Cardoso](https://github.com/thonecardoso) para fins educacionais.
