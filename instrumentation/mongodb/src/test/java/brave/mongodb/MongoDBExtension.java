/*
 * Copyright 2013-2023 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.time.Duration;
import java.util.Arrays;
import org.bson.Document;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import static brave.mongodb.ITMongoDBTracing.COLLECTION_NAME;
import static brave.mongodb.ITMongoDBTracing.DATABASE_NAME;
import static java.util.Collections.singletonList;
import static org.testcontainers.utility.DockerImageName.parse;

class MongoDBExtension implements BeforeAllCallback, AfterAllCallback {
  static final Logger LOGGER = LoggerFactory.getLogger(MongoDBExtension.class);
  static final int MONGODB_PORT = 27017;

  MongoDBContainer container = new MongoDBContainer();

  MongoClientSettings.Builder mongoClientSettingsBuilder() {
    return MongoClientSettings.builder().applyToClusterSettings(b -> b.hosts(singletonList(
      new ServerAddress(host(), port()))));
  }

  @Override public void beforeAll(ExtensionContext context) {
    if (context.getRequiredTestClass().getEnclosingClass() != null) {
      // Only run once in outermost scope.
      return;
    }

    container.start();
    LOGGER.info("Using hostPort " + host() + ":" + port());
    try (MongoClient mongoClient = MongoClients.create(mongoClientSettingsBuilder().build())) {
      MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
      MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);
      Document document1 = new Document("id", 1);
      Document document2 = new Document("id", 2);
      collection.insertMany(Arrays.asList(document1, document2));
    }
  }

  @Override public void afterAll(ExtensionContext context) {
    if (context.getRequiredTestClass().getEnclosingClass() != null) {
      // Only run once in outermost scope.
      return;
    }

    container.stop();
  }

  String host() {
    return container.getHost();
  }

  int port() {
    return container.getMappedPort(MONGODB_PORT);
  }

  // mostly waiting for https://github.com/testcontainers/testcontainers-java/issues/3537
  static final class MongoDBContainer extends GenericContainer<MongoDBContainer> {
    MongoDBContainer() {
      // Use OpenZipkin's small test image, which is multi-arch and doesn't consume Docker Hub quota
      super(parse("ghcr.io/openzipkin/mongodb-alpine:4.0.5"));
      if ("true".equals(System.getProperty("docker.skip"))) {
        throw new TestAbortedException("${docker.skip} == true");
      }
      withExposedPorts(MONGODB_PORT);
      waitStrategy = Wait.forLogMessage(".*waiting for connections.*", 1);
      withStartupTimeout(Duration.ofSeconds(60));
    }
  }
}
