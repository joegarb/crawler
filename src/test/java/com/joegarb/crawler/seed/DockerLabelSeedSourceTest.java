package com.joegarb.crawler.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for DockerLabelSeedSource. */
class DockerLabelSeedSourceTest {

  @Test
  void extractsHostsFromTraefikRouterRules() {
    String json =
        """
        [
          {"Labels": {"traefik.http.routers.app.rule": "Host(`app.example.com`)"}},
          {"Labels": {"traefik.http.routers.api.rule": "Host(`api.example.com`) && PathPrefix(`/v1`)"}}
        ]
        """;
    assertEquals(
        List.of("https://app.example.com/", "https://api.example.com/"),
        DockerLabelSeedSource.seedsFromContainersJson(json));
  }

  @Test
  void extractsMultipleHostsFromOneRule() {
    String json =
        """
        [{"Labels": {"traefik.http.routers.app.rule": "Host(`a.example.com`, `b.example.com`)"}}]
        """;
    assertEquals(
        List.of("https://a.example.com/", "https://b.example.com/"),
        DockerLabelSeedSource.seedsFromContainersJson(json));
  }

  @Test
  void ignoresNonRouterRuleLabels() {
    String json =
        """
        [
          {"Labels": {
            "traefik.enable": "true",
            "traefik.http.services.app.loadbalancer.server.port": "8080",
            "com.docker.compose.project": "Host(`not-a-rule.example.com`)"
          }},
          {"Labels": null},
          {}
        ]
        """;
    assertTrue(DockerLabelSeedSource.seedsFromContainersJson(json).isEmpty());
  }

  @Test
  void deduplicatesHostsAcrossContainers() {
    String json =
        """
        [
          {"Labels": {"traefik.http.routers.a.rule": "Host(`app.example.com`)"}},
          {"Labels": {"traefik.http.routers.b.rule": "Host(`app.example.com`)"}}
        ]
        """;
    assertEquals(
        List.of("https://app.example.com/"), DockerLabelSeedSource.seedsFromContainersJson(json));
  }

  @Test
  void readsContainersOverUnixSocket() throws Exception {
    Path socket = Files.createTempFile("dlss", ".sock");
    Files.delete(socket);
    String body =
        "[{\"Labels\": {\"traefik.http.routers.app.rule\": \"Host(`app.example.com`)\"}}]";
    String response = "HTTP/1.0 200 OK\r\nContent-Type: application/json\r\n\r\n" + body;

    try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
      server.bind(UnixDomainSocketAddress.of(socket));
      Thread.ofVirtual().start(() -> serveOnce(server, response));

      List<String> seeds = new DockerLabelSeedSource(socket.toString()).seeds();
      assertEquals(List.of("https://app.example.com/"), seeds);
    } finally {
      Files.deleteIfExists(socket);
    }
  }

  @Test
  void returnsEmptyWhenSocketIsUnavailable() {
    List<String> seeds = new DockerLabelSeedSource("/nonexistent/docker.sock").seeds();
    assertTrue(seeds.isEmpty());
  }

  private static void serveOnce(ServerSocketChannel server, String response) {
    try (SocketChannel client = server.accept()) {
      ByteBuffer request = ByteBuffer.allocate(8192);
      client.read(request);
      client.write(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
