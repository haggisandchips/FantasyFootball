package com.haggisandchips.fantasyfootball.service;

import java.io.IOException;
import java.net.URISyntaxException;

public interface TeamService {

  void process() throws IOException, URISyntaxException, InterruptedException;
}
