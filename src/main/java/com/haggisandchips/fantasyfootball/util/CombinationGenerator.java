package com.haggisandchips.fantasyfootball.util;

import java.util.List;

public interface CombinationGenerator<T> {

  boolean hasMore();

  List<T> getNext();
}
