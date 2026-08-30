package com.haggisandchips.fantasyfootball.util;

import java.util.List;

public interface PermutationGenerator<T> {

  boolean hasMore();

  List<T> getNext();
}
