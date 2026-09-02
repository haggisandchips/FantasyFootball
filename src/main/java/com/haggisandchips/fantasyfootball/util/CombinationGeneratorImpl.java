package com.haggisandchips.fantasyfootball.util;

import java.util.ArrayList;
import java.util.List;

public class CombinationGeneratorImpl<T> implements CombinationGenerator<T> {

  private final List<T> objects;

  private final int n;

  private final int r;

  private final int[] positions;

  private boolean hasMore = true;

  public CombinationGeneratorImpl(final List<T> objects, final int r) {

    this.r = r;
    this.n = objects.size();

    if (r > n) {
      throw new IllegalArgumentException(
          String.format(
              "Size of subset [%d] cannot exceed the number of elements available [%d].", r, n));
    }

    this.objects = objects;

    positions = new int[r];
    reset();
  }

  public boolean hasMore() {

    return hasMore;
  }

  public List<T> getNext() {

    if (!hasMore) {
      throw new IllegalStateException("There are no more combinations available.");
    }

    final List<T> combination = new ArrayList<>(r);

    for (int ii : positions) {
      combination.add(objects.get(ii));
    }

    int lastPos = incrementPosition(r - 1);

    if (lastPos >= n) {
      hasMore = false;
    }

    return combination;
  }

  private int incrementPosition(int ii) {

    positions[ii]++;
    if (ii > 0 && positions[ii] >= n - (r - 1 - ii)) {
      positions[ii] = incrementPosition(ii - 1) + 1;
    }

    return positions[ii];
  }

  private void reset() {

    for (int ii = 0; ii < positions.length; ii++) {
      positions[ii] = ii;
    }

    hasMore = true;
  }
}
