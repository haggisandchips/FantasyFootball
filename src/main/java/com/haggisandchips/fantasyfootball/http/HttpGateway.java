package com.haggisandchips.fantasyfootball.http;

import java.io.IOException;
import java.net.URI;

// Seam for outbound HTTP so a future authenticated implementation (cookies, OAuth headers, token
// refresh) can be swapped in without callers changing - they only ever ask for a URI.
public interface HttpGateway {

  String get(URI uri) throws IOException, InterruptedException;
}
