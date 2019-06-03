package org.apache.geode.cache.query.internal.index;

import org.apache.geode.internal.cache.RegionEntry;

public interface RangeIndexedExpressionEvaluator extends IndexedExpressionEvaluator {
  void evaluate(RegionEntry target, RangeIndexEvaluator.IndexUpdateOperation updateOperation)
      throws IMQException;

  /**
   * This function is used for creating Index data at the start
   */
  void initializeIndex(boolean loadEntries,
      RangeIndexEvaluator.IndexUpdateOperation updateOperation) throws IMQException;

  boolean isFirstItrOnKey();

  boolean isFirstItrOnEntry();

  interface IndexUpdateOperation {
    void add(Object indexKey, Object value, RegionEntry entry) throws IMQException;
  }
}
