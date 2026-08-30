package com.haggisandchips.fantasyfootball.report;

import com.haggisandchips.fantasyfootball.service.AnalysisResult;

// Presentation of an AnalysisResult, kept separate from TeamAnalysisService so a future UI can
// render the same result differently instead of scraping log output.
public interface AnalysisReporter {

  void report(AnalysisResult result);
}
