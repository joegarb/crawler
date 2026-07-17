package com.joegarb.crawler.seed;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers seed URLs from the labels of running Docker containers, by extracting the hosts from
 * Traefik router rules (e.g. {@code traefik.http.routers.app.rule=Host(`app.example.com`)}). Talks
 * to the Docker Engine API over its Unix socket; failures yield an empty seed list so a crawl can
 * still proceed from other sources.
 */
public class DockerLabelSeedSource implements SeedSource {
  private static final Logger logger = LoggerFactory.getLogger(DockerLabelSeedSource.class);
  private static final Pattern ROUTER_RULE_LABEL =
      Pattern.compile("^traefik\\.http\\.routers\\.[^.]+\\.rule$");
  private static final Pattern HOST_RULE = Pattern.compile("Host\\(([^)]*)\\)");
  private static final Pattern BACKTICKED = Pattern.compile("`([^`]+)`");

  private final Path socketPath;

  public DockerLabelSeedSource(String socketPath) {
    this.socketPath = Path.of(socketPath);
  }

  @Override
  public List<String> seeds() {
    try {
      String json = get("/containers/json");
      List<String> seeds = seedsFromContainersJson(json);
      logger.info("Discovered {} seed URL(s) from Docker container labels", seeds.size());
      return seeds;
    } catch (IOException e) {
      logger.warn("Failed to discover seeds from Docker socket {}: {}", socketPath, e.getMessage());
      return List.of();
    }
  }

  /**
   * Extracts seed URLs from a Docker {@code /containers/json} response: every host named in a
   * Traefik router rule label becomes an https seed.
   *
   * @param json the Docker API response body
   * @return the seed URLs, de-duplicated in discovery order
   */
  static List<String> seedsFromContainersJson(String json) {
    Set<String> seeds = new LinkedHashSet<>();
    JsonArray containers = JsonParser.parseString(json).getAsJsonArray();
    for (JsonElement container : containers) {
      JsonElement labels = container.getAsJsonObject().get("Labels");
      if (labels == null || !labels.isJsonObject()) {
        continue;
      }
      for (Map.Entry<String, JsonElement> label : ((JsonObject) labels).entrySet()) {
        if (!ROUTER_RULE_LABEL.matcher(label.getKey()).matches()) {
          continue;
        }
        Matcher hostRule = HOST_RULE.matcher(label.getValue().getAsString());
        while (hostRule.find()) {
          Matcher host = BACKTICKED.matcher(hostRule.group(1));
          while (host.find()) {
            seeds.add("https://" + host.group(1) + "/");
          }
        }
      }
    }
    return new ArrayList<>(seeds);
  }

  private String get(String path) throws IOException {
    try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
      channel.connect(UnixDomainSocketAddress.of(socketPath));
      // HTTP/1.0 so the daemon responds without chunked encoding and closes the connection
      String request = "GET " + path + " HTTP/1.0\r\nHost: docker\r\n\r\n";
      channel.write(ByteBuffer.wrap(request.getBytes(StandardCharsets.UTF_8)));

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ByteBuffer buffer = ByteBuffer.allocate(8192);
      while (channel.read(buffer) != -1) {
        buffer.flip();
        out.write(buffer.array(), 0, buffer.limit());
        buffer.clear();
      }
      String response = out.toString(StandardCharsets.UTF_8);

      int headerEnd = response.indexOf("\r\n\r\n");
      if (headerEnd < 0) {
        throw new IOException("malformed HTTP response from Docker socket");
      }
      String statusLine = response.substring(0, response.indexOf("\r\n"));
      if (!statusLine.contains(" 200 ")) {
        throw new IOException("Docker API returned: " + statusLine);
      }
      return response.substring(headerEnd + 4);
    }
  }
}
