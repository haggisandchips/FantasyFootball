package com.haggisandchips.fantasyfootball.helpers;

import java.util.List;

public interface PermutationGenerator<T> {

  boolean hasMore();

  List<T> getNext();
}
