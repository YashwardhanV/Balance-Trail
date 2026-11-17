package com.balancetrail.batch;

/**
 * Edit distance between two strings: how many single-character inserts, deletes or replacements
 * turn one into the other. "TXN-1008" vs "TXN-10088" is 1, vs "TXN-1080" is 2.
 */
public final class Levenshtein {
  private Levenshtein() {}

  /**
   * Returns the distance, or {@code limit + 1} as soon as it is known to be larger than
   * {@code limit} (we only care whether two IDs are "close", so there is no need to finish).
   */
  public static int distance(String a, String b, int limit) {
    if (Math.abs(a.length() - b.length()) > limit) {
      return limit + 1;
    }
    int[] previous = new int[b.length() + 1];
    int[] current = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      current[0] = i;
      int rowMinimum = current[0];
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
        rowMinimum = Math.min(rowMinimum, current[j]);
      }
      if (rowMinimum > limit) {
        return limit + 1;
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return Math.min(previous[b.length()], limit + 1);
  }
}
