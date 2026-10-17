package com.haggisandchips.fantasyfootball.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

// Makes outbound requests look like the FPL site's own frontend calling its API from Chrome: the
// same-origin fetch() headers Chrome attaches, and gzip/deflate response decoding. Left alone, the
// JDK HttpClient announces itself as "Java-http-client/<version>" and sends none of these.
//
// Chrome's UA reduction freezes everything after the major version at 0.0.0, so only the major
// needs keeping in step with the installed browser (here and in SEC_CH_UA).
//
// Deliberately not matched, because the JDK HttpClient gives no control over them: the TLS
// handshake fingerprint, HTTP/2 SETTINGS/priority frames, header ordering, and "br"/"zstd" in
// Accept-Encoding (advertising codings we can't decode would break responses).
public final class BrowserRequests {

  private static final String ORIGIN = "https://fantasy.premierleague.com";
  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
          + "Chrome/155.0.0.0 Safari/537.36";
  private static final String SEC_CH_UA =
      "\"Google Chrome\";v=\"155\", \"Chromium\";v=\"155\", \"Not_A Brand\";v=\"24\"";

  private BrowserRequests() {
  }

  // A client that behaves like a browser session: follows redirects and keeps cookies the server
  // sets (e.g. bot-management cookies) for replay on later calls, as Chrome's cookie jar would.
  public static HttpClient newClient() {

    return HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
        .build();
  }

  // A request builder for an FPL API call as the site's own JavaScript would make it. Origin is
  // only sent on POST - Chrome omits it from same-origin GETs.
  public static HttpRequest.Builder builder(final URI uri, final boolean post) {

    final HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
        .header("sec-ch-ua", SEC_CH_UA)
        .header("sec-ch-ua-mobile", "?0")
        .header("sec-ch-ua-platform", "\"Windows\"")
        .header("User-Agent", USER_AGENT)
        .header("Accept", "*/*")
        .header("Sec-Fetch-Site", "same-origin")
        .header("Sec-Fetch-Mode", "cors")
        .header("Sec-Fetch-Dest", "empty")
        .header("priority", "u=1, i")
        .header("Referer", ORIGIN + "/")
        .header("Accept-Encoding", "gzip, deflate")
        .header("Accept-Language", "en-GB,en-US;q=0.9,en;q=0.8");
    if (post) {
      builder.header("Origin", ORIGIN);
    }
    return builder;
  }

  public static HttpResponse<String> send(final HttpClient client, final HttpRequest request)
      throws IOException, InterruptedException {

    final HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    final String encoding = response.headers().firstValue("Content-Encoding").orElse("identity");

    try (InputStream raw = response.body();
         InputStream decoded = decode(raw, encoding)) {
      final String body = new String(decoded.readAllBytes(), StandardCharsets.UTF_8);
      return new DecodedResponse(response, body);
    }
  }

  private static InputStream decode(final InputStream raw, final String encoding) throws IOException {

    return switch (encoding.toLowerCase()) {
      case "gzip" -> new GZIPInputStream(raw);
      case "deflate" -> new InflaterInputStream(raw);
      default -> raw;
    };
  }

  private record DecodedResponse(HttpResponse<InputStream> source, String body) implements HttpResponse<String> {

    @Override
    public int statusCode() {
      return source.statusCode();
    }

    @Override
    public HttpRequest request() {
      return source.request();
    }

    @Override
    public java.util.Optional<HttpResponse<String>> previousResponse() {
      return java.util.Optional.empty();
    }

    @Override
    public java.net.http.HttpHeaders headers() {
      return source.headers();
    }

    @Override
    public URI uri() {
      return source.uri();
    }

    @Override
    public HttpClient.Version version() {
      return source.version();
    }

    @Override
    public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
      return source.sslSession();
    }
  }
}
