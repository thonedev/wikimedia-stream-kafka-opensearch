package org.example;

import com.google.gson.JsonParser;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.common.xcontent.XContentType;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class OpenSearchConsumer {
    public static void main(String[] args) throws IOException {
        final var log = LoggerFactory.getLogger(OpenSearchConsumer.class);

        log.info("Create Kafka consumer");
        KafkaConsumer<String, String> consumer = createKafkaConsumer();

        final var mainThread = Thread.currentThread();

        // adding the shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Detected shutdown, closing Kafka consumer...");
            consumer.wakeup(); // Important: wakeup any blocking poll()
            log.info("Kafka consumer closed.");

            try {
                mainThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));

        log.info("Create an OpenSearch Client");
        var openSearchClient = createOpenSearchClient();


        // we need to create the index on OpenSearch if it doesn't exist already
        try (openSearchClient; consumer) {
            var indexExists = openSearchClient.indices().exists(new GetIndexRequest("wikimedia"), RequestOptions.DEFAULT);

            if (!indexExists) {
                var createIndexReequest = new CreateIndexRequest("wikimedia");
                openSearchClient.indices().create(createIndexReequest, RequestOptions.DEFAULT);
                log.info("The Wikimedia Index has been created");
            } else {
                log.info("The Wikimedia Index already exists");
            }

            consumer.subscribe(Collections.singleton("wikimedia.recentchange"));

            while (true) {
                var records = consumer.poll(Duration.ofMillis(3000));

                var recordCount = records.count();
                log.info("received {} record(s)", recordCount);

                var bulkRequest = new BulkRequest();

                for (var record : records) {
                    // strategy 1 to prevent to lost messages without processing
                    // define an ID using Kafka Record coordinates
                    //var id = String.format("%s-%s-%s", record.topic(), record.partition(), record.offset());

                    // strategy 2
                    var id = extractId(record.value());

                    // send the record into OpenSearch
                    try {
                        var indexRequest = new IndexRequest("wikimedia")
                                .source(record.value(), XContentType.JSON)
                                .id(id);

//                        var response = openSearchClient.index(indexRequest, RequestOptions.DEFAULT);

                        bulkRequest.add(indexRequest);

                        //log.info("Inserted 1 document into OpenSearch ID: {}", response.getId());
                    } catch (Exception e) {

                    }
                }

                if (bulkRequest.numberOfActions() > 0) {
                    var bulkResponse = openSearchClient.bulk(bulkRequest, RequestOptions.DEFAULT);
                    log.info("Inserted {} record(s)", bulkResponse.getItems().length);


                    // commit offsets after the batch is consumed
                    consumer.commitSync();

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }


            }

        } catch (WakeupException e) {
            log.info("Consumer is starting to shut down");
        } catch (Exception e) {
            log.error("Unexpected exception in the consumer");
        } finally {
            consumer.close();
            openSearchClient.close();
            log.info("The consumer is now gracefully shut down");
        }
    }

    private static String extractId(String json) {
        return JsonParser.parseString(json)
                .getAsJsonObject()
                .get("meta")
                .getAsJsonObject()
                .get("id")
                .getAsString();
    }

    private static KafkaConsumer<String, String> createKafkaConsumer() {
        var bootstrapServers = "127.0.0.1:9092";
        var groupId = "open-search-consumer-wikimedia";
        var topic = "wikimedia";

        // create Producer properties
        Properties properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");


        return new KafkaConsumer<>(properties);
    }

    static RestHighLevelClient createOpenSearchClient() {
        var connString = "http://localhost:9200";
        //var connString = "https://ekzkdy333h:89cg9fh9fb@thonedev-search-8746929749.us-east-1.bonsaisearch.net:443";

        URI connUri = URI.create(connString);
        String userInfo = connUri.getUserInfo();

        RestClientBuilder builder = RestClient.builder(
                new HttpHost(connUri.getHost(), connUri.getPort(), connUri.getScheme())
        );

        if (userInfo != null) {
            // If authentication info is provided
            String[] auth = userInfo.split(":");
            String username = auth[0];
            String password = auth[1];

            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password));

            builder.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                @Override
                public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
                    return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                }
            });
        }

        return new RestHighLevelClient(builder);
    }
}