package ua.uzhnu.collab.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Spring configuration for the embedded chat server. */
@Configuration
@Profile("!test")
public class EmbeddedChatConfiguration {

  @Bean(initMethod = "start", destroyMethod = "stop")
  public EmbeddedChatServer embeddedChatServer(
      @Value("${chat.embedded.host:127.0.0.1}") String host,
      @Value("${chat.embedded.port:0}") int port) {
    return new EmbeddedChatServer(host, port);
  }
}

