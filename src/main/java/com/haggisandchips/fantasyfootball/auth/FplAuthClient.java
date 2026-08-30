package com.haggisandchips.fantasyfootball.auth;

import java.io.IOException;
import java.net.URI;

// A GET that carries whatever session the FPL login flow produced (cookies today - the detail
// callers shouldn't need to know about, same spirit as HttpGateway for the unauthenticated side).
public interface FplAuthClient {

  String authenticatedGet(URI uri) throws IOException, InterruptedException;
}
