package com.haggisandchips.fantasyfootball.service;

import java.io.IOException;

public interface TeamAnalysisService {

  AnalysisResult analyse() throws IOException, InterruptedException;
}
