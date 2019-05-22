/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.cache.query.internal.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.query.AmbiguousNameException;
import org.apache.geode.cache.query.FunctionDomainException;
import org.apache.geode.cache.query.Index;
import org.apache.geode.cache.query.IndexStatistics;
import org.apache.geode.cache.query.NameResolutionException;
import org.apache.geode.cache.query.QueryInvocationTargetException;
import org.apache.geode.cache.query.QueryService;
import org.apache.geode.cache.query.SelectResults;
import org.apache.geode.cache.query.TypeMismatchException;
import org.apache.geode.cache.query.internal.CompiledID;
import org.apache.geode.cache.query.internal.CompiledIndexOperation;
import org.apache.geode.cache.query.internal.CompiledOperation;
import org.apache.geode.cache.query.internal.CompiledPath;
import org.apache.geode.cache.query.internal.CompiledValue;
import org.apache.geode.cache.query.internal.CqEntry;
import org.apache.geode.cache.query.internal.DefaultQuery;
import org.apache.geode.cache.query.internal.ExecutionContext;
import org.apache.geode.cache.query.internal.IndexInfo;
import org.apache.geode.cache.query.internal.RuntimeIterator;
import org.apache.geode.cache.query.internal.StructFields;
import org.apache.geode.cache.query.internal.StructImpl;
import org.apache.geode.cache.query.internal.Support;
import org.apache.geode.cache.query.internal.index.IndexStore.IndexStoreEntry;
import org.apache.geode.cache.query.internal.parse.OQLLexerTokenTypes;
import org.apache.geode.cache.query.internal.types.StructTypeImpl;
import org.apache.geode.cache.query.types.ObjectType;
import org.apache.geode.internal.Assert;
import org.apache.geode.internal.cache.BucketRegion;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.internal.cache.PartitionedRegion;
import org.apache.geode.internal.cache.RegionEntry;
import org.apache.geode.internal.cache.partitioned.Bucket;
import org.apache.geode.internal.cache.persistence.query.CloseableIterator;
import org.apache.geode.internal.logging.LogService;
import org.apache.geode.internal.offheap.annotations.Retained;
import org.apache.geode.pdx.PdxInstance;
import org.apache.geode.pdx.internal.PdxString;

/**
 * This class implements abstract algorithms common to all indexes, such as index creation, use of a
 * path evaluator object, etc. It serves as the factory for a path evaluator object and maintains
 * the path evaluator object to use for index creation and index maintenance. It also maintains a
 * reference to the root collection on which the index is created. This class also implements the
 * abstract methods to add and remove entries to an underlying storage structure (e.g. a btree), and
 * as part of this algorithm, maintains a map of entries that map to null at the end of the index
 * path, and entries that cannot be traversed to the end of the index path (traversal is undefined).
 */
public abstract class AbstractIndex implements IndexProtocol {
  private static final Logger logger = LogService.getLogger();

  final InternalCache cache;

  final String indexName;

  final Region region;

  final String indexedExpression;

  final String fromClause;

  final String projectionAttributes;

  final String originalIndexedExpression;

  final String originalFromClause;

  private final String originalProjectionAttributes;

  final String[] canonicalizedDefinitions;

  private boolean isValid;

  InternalIndexStatistics internalIndexStats;

  /** For PartitionedIndex for now */
  protected Index prIndex;

  /**
   * Flag to indicate if index map has keys as PdxString All the keys in the index map should be
   * either Strings or PdxStrings
   */
  private Boolean isIndexedPdxKeys = false;

  /** Flag to indicate if the flag isIndexedPdxKeys is set */
  Boolean isIndexedPdxKeysFlagSet = false;

  boolean indexOnRegionKeys = false;

  boolean indexOnValues = false;

  private final ReadWriteLock removeIndexLock = new ReentrantReadWriteLock();

  /** Flag to indicate if the index is populated with data */
  volatile boolean isPopulated = false;

  AbstractIndex(InternalCache cache, String indexName, Region region, String fromClause,
      String indexedExpression, String projectionAttributes, String originalFromClause,
      String originalIndexedExpression, String[] defintions, IndexStatistics stats) {
    this.cache = cache;
    this.indexName = indexName;
    this.region = region;
    this.indexedExpression = indexedExpression;
    this.fromClause = fromClause;
    this.originalIndexedExpression = originalIndexedExpression;
    this.originalFromClause = originalFromClause;
    this.canonicalizedDefinitions = defintions;
    if (StringUtils.isEmpty(projectionAttributes)) {
      projectionAttributes = "*";
    }
    this.projectionAttributes = projectionAttributes;
    this.originalProjectionAttributes = projectionAttributes;
    if (stats != null) {
      this.internalIndexStats = (InternalIndexStatistics) stats;
    } else {
      this.internalIndexStats = createStats(indexName);
    }
  }

  /**
   * Must be implemented by all implementing classes iff they have any forward map for
   * index-key->RE.
   *
   * @return the forward map of respective index.
   */
  public Map getValueToEntriesMap() {
    return null;
  }

  /**
   * Get statistics information for this index.
   */
  @Override
  public IndexStatistics getStatistics() {
    return this.internalIndexStats;
  }

  @Override
  public void destroy() {
    markValid(false);
    if (this.internalIndexStats != null) {
      this.internalIndexStats.updateNumKeys(0);
      this.internalIndexStats.close();
    }
  }

  long updateIndexUpdateStats() {
    long result = System.nanoTime();
    this.internalIndexStats.incUpdatesInProgress(1);
    return result;
  }

  void updateIndexUpdateStats(long start) {
    long end = System.nanoTime();
    this.internalIndexStats.incUpdatesInProgress(-1);
    this.internalIndexStats.incUpdateTime(end - start);
  }

  long updateIndexUseStats() {
    return updateIndexUseStats(true);
  }

  long updateIndexUseStats(boolean updateStats) {
    long result = 0;
    if (updateStats) {
      this.internalIndexStats.incUsesInProgress(1);
      result = System.nanoTime();
    }
    return result;
  }

  void updateIndexUseEndStats(long start) {
    updateIndexUseEndStats(start, true);
  }

  void updateIndexUseEndStats(long start, boolean updateStats) {
    if (updateStats) {
      long end = System.nanoTime();
      this.internalIndexStats.incUsesInProgress(-1);
      this.internalIndexStats.incNumUses();
      this.internalIndexStats.incUseTime(end - start);
    }
  }

  /**
   * The Region this index is on
   *
   * @return the Region for this index
   */
  @Override
  public Region getRegion() {
    return this.region;
  }

  /**
   * Returns the unique name of this index
   */
  @Override
  public String getName() {
    return this.indexName;
  }

  @Override
  public void query(Object key, int operator, Collection results, ExecutionContext context)
      throws TypeMismatchException, FunctionDomainException, NameResolutionException,
      QueryInvocationTargetException {

    // get a read lock when doing a lookup
    if (context.getBucketList() != null && this.region instanceof BucketRegion) {
      PartitionedRegion pr = ((Bucket) this.region).getPartitionedRegion();
      long start = updateIndexUseStats();
      try {
        for (Object bucketId : context.getBucketList()) {
          AbstractIndex bucketIndex =
              PartitionedIndex.getBucketIndex(pr, this.indexName, (Integer) bucketId);
          if (bucketIndex == null) {
            continue;
          }
          bucketIndex.lockedQuery(key, operator, results, null/* No Keys to be removed */, context);

        }
      } finally {
        updateIndexUseEndStats(start);
      }
    } else {
      long start = updateIndexUseStats();
      try {
        lockedQuery(key, operator, results, null/* No Keys to be removed */, context);
      } finally {
        updateIndexUseEndStats(start);
      }
    }
  }

  @Override
  public void query(Object key, int operator, Collection results, @Retained CompiledValue iterOp,
      RuntimeIterator indpndntItr, ExecutionContext context, List projAttrib,
      SelectResults intermediateResults, boolean isIntersection) throws TypeMismatchException,
      FunctionDomainException, NameResolutionException, QueryInvocationTargetException {

    // get a read lock when doing a lookup
    if (context.getBucketList() != null && this.region instanceof BucketRegion) {
      PartitionedRegion pr = ((Bucket) region).getPartitionedRegion();
      long start = updateIndexUseStats();
      try {
        for (Object bucketId : context.getBucketList()) {
          AbstractIndex bucketIndex =
              PartitionedIndex.getBucketIndex(pr, this.indexName, (Integer) bucketId);
          if (bucketIndex == null) {
            continue;
          }
          bucketIndex.lockedQuery(key, operator, results, iterOp, indpndntItr, context, projAttrib,
              intermediateResults, isIntersection);
        }
      } finally {
        updateIndexUseEndStats(start);
      }
    } else {
      long start = updateIndexUseStats();
      try {
        lockedQuery(key, operator, results, iterOp, indpndntItr, context, projAttrib,
            intermediateResults, isIntersection);
      } finally {
        updateIndexUseEndStats(start);
      }
    }
  }

  @Override
  public void query(Object key, int operator, Collection results, Set keysToRemove,
      ExecutionContext context) throws TypeMismatchException, FunctionDomainException,
      NameResolutionException, QueryInvocationTargetException {

    // get a read lock when doing a lookup
    if (context.getBucketList() != null && this.region instanceof BucketRegion) {
      PartitionedRegion pr = ((Bucket) region).getPartitionedRegion();
      long start = updateIndexUseStats();
      try {
        for (Object bucketId : context.getBucketList()) {
          AbstractIndex bucketIndex =
              PartitionedIndex.getBucketIndex(pr, this.indexName, (Integer) bucketId);
          if (bucketIndex == null) {
            continue;
          }
          bucketIndex.lockedQuery(key, operator, results, keysToRemove, context);
        }
      } finally {
        updateIndexUseEndStats(start);
      }
    } else {
      long start = updateIndexUseStats();
      try {
        lockedQuery(key, operator, results, keysToRemove, context);
      } finally {
        updateIndexUseEndStats(start);
      }
    }
  }

  @Override
  public void query(Collection results, Set keysToRemove, ExecutionContext context)
      throws TypeMismatchException, FunctionDomainException, NameResolutionException,
      QueryInvocationTargetException {

    Iterator iterator = keysToRemove.iterator();
    Object temp = iterator.next();
    iterator.remove();
    if (context.getBucketList() != null && this.region instanceof BucketRegion) {
      long start = updateIndexUseStats();
      try {
        PartitionedRegion partitionedRegion = ((Bucket) this.region).getPartitionedRegion();
        for (Object bucketId : context.getBucketList()) {
          AbstractIndex bucketIndex = PartitionedIndex.getBucketIndex(partitionedRegion,
              this.indexName, (Integer) bucketId);
          if (bucketIndex == null) {
            continue;
          }
          bucketIndex.lockedQuery(temp, OQLLexerTokenTypes.TOK_NE, results,
              iterator.hasNext() ? keysToRemove : null, context);
        }
      } finally {
        updateIndexUseEndStats(start);
      }
    } else {
      long start = updateIndexUseStats();
      try {
        lockedQuery(temp, OQLLexerTokenTypes.TOK_NE, results,
            iterator.hasNext() ? keysToRemove : null, context);
      } finally {
        updateIndexUseEndStats(start);
      }
    }
  }

  @Override
  public void query(Object lowerBoundKey, int lowerBoundOperator, Object upperBoundKey,
      int upperBoundOperator, Collection results, Set keysToRemove, ExecutionContext context)
      throws TypeMismatchException, FunctionDomainException, NameResolutionException,
      QueryInvocationTargetException {

    if (context.getBucketList() != null) {
      if (this.region instanceof BucketRegion) {
        PartitionedRegion partitionedRegion = ((Bucket) this.region).getPartitionedRegion();
        long start = updateIndexUseStats();
        try {
          for (Object bucketId : context.getBucketList()) {
            AbstractIndex bucketIndex = PartitionedIndex.getBucketIndex(partitionedRegion,
                this.indexName, (Integer) bucketId);
            if (bucketIndex == null) {
              continue;
            }
            bucketIndex.lockedQuery(lowerBoundKey, lowerBoundOperator, upperBoundKey,
                upperBoundOperator, results, keysToRemove, context);
          }
        } finally {
          updateIndexUseEndStats(start);
        }
      }
    } else {
      long start = updateIndexUseStats();
      try {
        lockedQuery(lowerBoundKey, lowerBoundOperator, upperBoundKey, upperBoundOperator, results,
            keysToRemove, context);
      } finally {
        updateIndexUseEndStats(start);
      }
    }
  }

  @Override
  public List queryEquijoinCondition(IndexProtocol index, ExecutionContext context)
      throws TypeMismatchException, FunctionDomainException, NameResolutionException,
      QueryInvocationTargetException {

    Support.assertionFailed(
        " This function should have never got invoked as its meaningful implementation is present only in RangeIndex class");
    return null;
  }

  /**
   * Get the projectionAttributes for this expression.
   *
   * @return the projectionAttributes, or "*" if there were none specified at index creation.
   */
  @Override
  public String getProjectionAttributes() {
    return this.originalProjectionAttributes;
  }

  /**
   * Get the projectionAttributes for this expression.
   *
   * @return the projectionAttributes, or "*" if there were none specified at index creation.
   */
  @Override
  public String getCanonicalizedProjectionAttributes() {
    return this.projectionAttributes;
  }

  /**
   * Get the Original indexedExpression for this index.
   */
  @Override
  public String getIndexedExpression() {
    return this.originalIndexedExpression;
  }

  /**
   * Get the Canonicalized indexedExpression for this index.
   */
  @Override
  public String getCanonicalizedIndexedExpression() {
    return this.indexedExpression;
  }

  /**
   * Get the original fromClause for this index.
   */
  @Override
  public String getFromClause() {
    return this.originalFromClause;
  }

  /**
   * Get the canonicalized fromClause for this index.
   */
  @Override
  public String getCanonicalizedFromClause() {
    return this.fromClause;
  }

  public boolean isMapType() {
    return false;
  }

  @Override
  public boolean addIndexMapping(RegionEntry entry) throws IMQException {
    addMapping(entry);

    // if no exception, then success
    return true;
  }

  @Override
  public boolean addAllIndexMappings(Collection<RegionEntry> c) throws IMQException {
    for (RegionEntry regionEntry : c) {
      addMapping(regionEntry);
    }
    // if no exception, then success
    return true;
  }

  /**
   * @param opCode one of OTHER_OP, BEFORE_UPDATE_OP, AFTER_UPDATE_OP.
   */
  @Override
  public boolean removeIndexMapping(RegionEntry entry, int opCode) throws IMQException {
    removeMapping(entry, opCode);
    // if no exception, then success
    return true;
  }

  @Override
  public boolean removeAllIndexMappings(Collection<RegionEntry> c) throws IMQException {
    for (RegionEntry regionEntry : c) {
      removeMapping(regionEntry, OTHER_OP);
    }
    // if no exception, then success
    return true;
  }


  @Override
  public boolean isValid() {
    return this.isValid;
  }

  @Override
  public void markValid(boolean b) {
    this.isValid = b;
  }

  @Override
  public boolean isMatchingWithIndexExpression(CompiledValue condnExpr, String condnExprStr,
      ExecutionContext context)
      throws AmbiguousNameException, TypeMismatchException, NameResolutionException {
    return this.indexedExpression.equals(condnExprStr);
  }

  // package-private to avoid synthetic accessor
  Object verifyAndGetPdxDomainObject(Object value) {
    if (value instanceof StructImpl) {
      // Doing hasPdx check first, since its cheaper.
      if (((StructImpl) value).isHasPdx()
          && !((InternalCache) this.region.getCache()).getPdxReadSerializedByAnyGemFireServices()) {
        // Set the pdx values for the struct object.
        StructImpl v = (StructImpl) value;
        Object[] fieldValues = v.getPdxFieldValues();
        return new StructImpl((StructTypeImpl) v.getStructType(), fieldValues);
      }
    } else if (value instanceof PdxInstance
        && !((InternalCache) this.region.getCache()).getPdxReadSerializedByAnyGemFireServices()) {
      return ((PdxInstance) value).getObject();
    }
    return value;
  }

  private void addToResultsWithUnionOrIntersection(Collection results,
      SelectResults intermediateResults, boolean isIntersection, Object value) {
    value = verifyAndGetPdxDomainObject(value);

    if (intermediateResults == null) {
      results.add(value);
    } else {
      if (isIntersection) {
        int numOcc = intermediateResults.occurrences(value);
        if (numOcc > 0) {
          results.add(value);
          intermediateResults.remove(value);
        }
      } else {
        results.add(value);
      }
    }
  }

  private void addToStructsWithUnionOrIntersection(Collection results,
      SelectResults intermediateResults, boolean isIntersection, Object[] values) {

    for (int i = 0; i < values.length; i++) {
      values[i] = verifyAndGetPdxDomainObject(values[i]);
    }

    if (intermediateResults == null) {
      if (results instanceof StructFields) {
        ((StructFields) results).addFieldValues(values);
      } else {
        // The results could be LinkedStructSet or SortedResultsBag or StructSet
        SelectResults selectResults = (SelectResults) results;
        StructImpl structImpl = new StructImpl(
            (StructTypeImpl) selectResults.getCollectionType().getElementType(), values);
        selectResults.add(structImpl);
      }

    } else {
      if (isIntersection) {
        if (results instanceof StructFields) {
          int occurrences = intermediateResults.occurrences(values);
          if (occurrences > 0) {
            ((StructFields) results).addFieldValues(values);
            ((StructFields) intermediateResults).removeFieldValues(values);
          }

        } else {
          // could be LinkedStructSet or SortedResultsBag
          SelectResults selectResults = (SelectResults) results;
          StructImpl structImpl = new StructImpl(
              (StructTypeImpl) selectResults.getCollectionType().getElementType(), values);
          if (intermediateResults.remove(structImpl)) {
            selectResults.add(structImpl);
          }
        }

      } else {
        if (results instanceof StructFields) {
          ((StructFields) results).addFieldValues(values);
        } else {
          // could be LinkedStructSet or SortedResultsBag
          SelectResults selectResults = (SelectResults) results;
          StructImpl structImpl = new StructImpl(
              (StructTypeImpl) selectResults.getCollectionType().getElementType(), values);
          if (intermediateResults.remove(structImpl)) {
            selectResults.add(structImpl);
          }
        }
      }
    }
  }

  void applyCqOrProjection(List projAttrib, ExecutionContext context, Collection result,
      Object iterValue, SelectResults intermediateResults, boolean isIntersection, Object key)
      throws FunctionDomainException, TypeMismatchException, NameResolutionException,
      QueryInvocationTargetException {
    if (context != null && context.isCqQueryContext()) {
      result.add(new CqEntry(key, iterValue));
    } else {
      applyProjection(projAttrib, context, result, iterValue, intermediateResults, isIntersection);
    }
  }

  void applyProjection(List projAttrib, ExecutionContext context, Collection result,
      Object iterValue, SelectResults intermediateResults, boolean isIntersection)
      throws FunctionDomainException, TypeMismatchException, NameResolutionException,
      QueryInvocationTargetException {

    if (projAttrib == null) {
      iterValue = deserializePdxForLocalDistinctQuery(context, iterValue);
      this.addToResultsWithUnionOrIntersection(result, intermediateResults, isIntersection,
          iterValue);

    } else {
      boolean isStruct = result instanceof SelectResults
          && ((SelectResults) result).getCollectionType().getElementType() != null
          && ((SelectResults) result).getCollectionType().getElementType().isStructType();

      if (isStruct) {
        int projCount = projAttrib.size();
        Object[] values = new Object[projCount];
        Iterator projIter = projAttrib.iterator();
        int i = 0;
        while (projIter.hasNext()) {
          Object[] projDef = (Object[]) projIter.next();
          values[i] = deserializePdxForLocalDistinctQuery(context,
              ((CompiledValue) projDef[1]).evaluate(context));
          i++;
        }
        this.addToStructsWithUnionOrIntersection(result, intermediateResults, isIntersection,
            values);
      } else {
        Object[] temp = (Object[]) projAttrib.get(0);
        Object val = deserializePdxForLocalDistinctQuery(context,
            ((CompiledValue) temp[1]).evaluate(context));
        this.addToResultsWithUnionOrIntersection(result, intermediateResults, isIntersection, val);
      }
    }
  }

  /**
   * For local queries with distinct, deserialize all PdxInstances as we do not have a way to
   * compare Pdx and non Pdx objects in case the cache has a mix of pdx and non pdx objects. We
   * still have to honor the cache level readSerialized flag in case of all Pdx objects in cache.
   * Also always convert PdxString to String before adding to resultSet for remote queries
   */
  private Object deserializePdxForLocalDistinctQuery(ExecutionContext context, Object value)
      throws QueryInvocationTargetException {

    if (!((DefaultQuery) context.getQuery()).isRemoteQuery()) {
      if (context.isDistinct() && value instanceof PdxInstance
          && !this.region.getCache().getPdxReadSerialized()) {
        try {
          value = ((PdxInstance) value).getObject();
        } catch (Exception ex) {
          throw new QueryInvocationTargetException(
              "Unable to retrieve domain object from PdxInstance while building the ResultSet. "
                  + ex.getMessage());
        }
      } else if (value instanceof PdxString) {
        value = value.toString();
      }
    }
    return value;
  }

  private void removeFromResultsWithUnionOrIntersection(Collection results,
      SelectResults intermediateResults, boolean isIntersection, Object value) {

    if (intermediateResults == null) {
      results.remove(value);
    } else {
      if (isIntersection) {
        int numOcc = ((SelectResults) results).occurrences(value);
        if (numOcc > 0) {
          results.remove(value);
          intermediateResults.add(value);
        }
      } else {
        results.remove(value);
      }
    }
  }

  private void removeFromStructsWithUnionOrIntersection(Collection results,
      SelectResults intermediateResults, boolean isIntersection, Object[] values) {

    if (intermediateResults == null) {
      ((StructFields) results).removeFieldValues(values);
    } else {
      if (isIntersection) {
        int numOcc = ((SelectResults) results).occurrences(values);
        if (numOcc > 0) {
          ((StructFields) results).removeFieldValues(values);
          ((StructFields) intermediateResults).addFieldValues(values);

        }
      } else {
        ((StructFields) results).removeFieldValues(values);
      }
    }
  }

  private void removeProjection(List projAttrib, ExecutionContext context, Collection result,
      Object iterValue, SelectResults intermediateResults, boolean isIntersection)
      throws FunctionDomainException, TypeMismatchException, NameResolutionException,
      QueryInvocationTargetException {

    if (projAttrib == null) {
      this.removeFromResultsWithUnionOrIntersection(result, intermediateResults, isIntersection,
          iterValue);
    } else {
      if (result instanceof StructFields) {
        int projCount = projAttrib.size();
        Object[] values = new Object[projCount];
        Iterator projIter = projAttrib.iterator();
        int i = 0;
        while (projIter.hasNext()) {
          Object projDef[] = (Object[]) projIter.next();
          values[i++] = ((CompiledValue) projDef[1]).evaluate(context);
        }
        this.removeFromStructsWithUnionOrIntersection(result, intermediateResults, isIntersection,
            values);
      } else {
        Object[] temp = (Object[]) projAttrib.get(0);
        Object val = ((CompiledValue) temp[1]).evaluate(context);
        this.removeFromResultsWithUnionOrIntersection(result, intermediateResults, isIntersection,
            val);
      }
    }

  }

  /**
   * This function returns the canonicalized definitions of the from clauses used in Index creation
   */
  @Override
  public String[] getCanonicalizedIteratorDefinitions() {
    return this.canonicalizedDefinitions;
  }

  /**
   * This implementation is for PrimaryKeyIndex. RangeIndex has its own implementation. For
   * PrimaryKeyIndex , this method should not be used
   * <p>
   * TODO: check if an Exception should be thrown if the function implementation of this class gets
   * invoked
   */
  @Override
  public boolean containsEntry(RegionEntry entry) {
    return false;
  }

  abstract void instantiateEvaluator(IndexCreationHelper indexCreationHelper);

  @Override
  public void initializeIndex(boolean loadEntries) throws IMQException {
    // implement me
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Index [");
    sb.append(" Name=").append(getName());
    sb.append(" Type =").append(getType());
    sb.append(" IdxExp=").append(getIndexedExpression());
    sb.append(" From=").append(getFromClause());
    sb.append(" Proj=").append(getProjectionAttributes());
    sb.append(']');
    return sb.toString();
  }

  public abstract boolean isEmpty();

  protected abstract boolean isCompactRangeIndex();

  protected abstract InternalIndexStatistics createStats(String indexName);

  @Override
  public abstract ObjectType getResultSetType();

  abstract void recreateIndexData() throws IMQException;

  abstract void addMapping(RegionEntry entry) throws IMQException;

  abstract void removeMapping(RegionEntry entry, int opCode) throws IMQException;

  abstract void addMapping(Object key, Object value, RegionEntry entry) throws IMQException;

  /** Lookup method used when appropriate lock is held */
  abstract void lockedQuery(Object key, int operator, Collection results, CompiledValue iterOps,
      RuntimeIterator indpndntItr, ExecutionContext context, List projAttrib,
      SelectResults intermediateResults, boolean isIntersection) throws TypeMismatchException,
      FunctionDomainException, NameResolutionException, QueryInvocationTargetException;

  abstract void lockedQuery(Object lowerBoundKey, int lowerBoundOperator, Object upperBoundKey,
      int upperBoundOperator, Collection results, Set keysToRemove, ExecutionContext context)
      throws TypeMismatchException, FunctionDomainException, NameResolutionException,
      QueryInvocationTargetException;

  abstract void lockedQuery(Object key, int operator, Collection results, Set keysToRemove,
      ExecutionContext context) throws TypeMismatchException, FunctionDomainException,
      NameResolutionException, QueryInvocationTargetException;

  public Index getPRIndex() {
    return this.prIndex;
  }

  void setPRIndex(Index parIndex) {
    this.prIndex = parIndex;
  }

  /**
   * Dummy implementation that subclasses can override.
   */
  protected abstract static class InternalIndexStatistics implements IndexStatistics {
    @Override
    public long getNumUpdates() {
      return 0L;
    }

    @Override
    public long getTotalUpdateTime() {
      return 0L;
    }

    @Override
    public long getTotalUses() {
      return 0L;
    }

    @Override
    public long getNumberOfKeys() {
      return 0L;
    }

    @Override
    public long getNumberOfValues() {
      return 0L;
    }

    @Override
    public long getNumberOfValues(Object key) {
      return 0L;
    }

    @Override
    public int getReadLockCount() {
      return 0;
    }

    @Override
    public long getNumberOfMapIndexKeys() {
      return 0;
    }

    @Override
    public int getNumberOfBucketIndexes() {
      return 0;
    }

    public void close() {}

    public void incNumValues(int delta) {}

    public void incNumUpdates() {}

    public void incNumUpdates(int delta) {}

    public void incUpdatesInProgress(int delta) {}

    public void incUsesInProgress(int delta) {}

    public void updateNumKeys(long count) {}

    public void incNumKeys(long count) {}

    public void incNumMapIndexKeys(long numKeys) {}

    public void incUpdateTime(long delta) {}

    public void incNumUses() {}

    public void incUseTime(long delta) {}

    public void incReadLockCount(int delta) {}

    public void incNumBucketIndexes(int delta) {}
  }

  /**
   * Checks the limit for the resultset for distinct and non-distinct queries separately. In case of
   * non-distinct distinct elements size of result-set is matched against limit passed in as an
   * argument.
   *
   * @return true if limit is satisfied.
   */
  boolean verifyLimit(Collection result, int limit) {
    return limit > 0 && result.size() == limit;
  }



  /**
   * Matches the Collection reference in given context for this index's from-clause in all current
   * independent collection references associated to the context. For example, if a join Query has
   * "/region1 p, region2 e" from clause context contains two region references for p and e and
   * Index could be used for any of those of both of those regions.
   *
   * Note: This Index contains its own from clause definition which corresponds to a region
   * collection reference in given context and must be contained at 0th index in
   * {@link AbstractIndex#canonicalizedDefinitions}.
   *
   * @return {@link RuntimeIterator} this should not be null ever.
   */
  RuntimeIterator getRuntimeIteratorForThisIndex(ExecutionContext context) {
    List<RuntimeIterator> indItrs = context.getCurrentIterators();
    Region rgn = this.getRegion();
    if (rgn instanceof BucketRegion) {
      rgn = ((Bucket) rgn).getPartitionedRegion();
    }
    String regionPath = rgn.getFullPath();
    String definition = this.getCanonicalizedIteratorDefinitions()[0];
    for (RuntimeIterator itr : indItrs) {
      if (itr.getDefinition().equals(regionPath) || itr.getDefinition().equals(definition)) {
        return itr;
      }
    }
    return null;
  }

  /**
   * Similar to {@link #getRuntimeIteratorForThisIndex(ExecutionContext)} except that this one also
   * matches the iterator name if present with alias used in the {@link IndexInfo}
   *
   * @return {@link RuntimeIterator}
   */
  RuntimeIterator getRuntimeIteratorForThisIndex(ExecutionContext context, IndexInfo info) {
    List<RuntimeIterator> indItrs = context.getCurrentIterators();
    Region rgn = this.getRegion();
    if (rgn instanceof BucketRegion) {
      rgn = ((Bucket) rgn).getPartitionedRegion();
    }
    String regionPath = rgn.getFullPath();
    String definition = this.getCanonicalizedIteratorDefinitions()[0];
    for (RuntimeIterator itr : indItrs) {
      if (itr.getDefinition().equals(regionPath) || itr.getDefinition().equals(definition)) {
        // if iterator has name alias must be used in the query
        if (itr.getName() != null) {
          CompiledValue path = info._path();
          // match the iterator name with alias
          String pathName = getReceiverNameFromPath(path);
          if (path.getType() == OQLLexerTokenTypes.Identifier || itr.getName().equals(pathName)) {
            return itr;
          }
        } else {
          return itr;
        }
      }
    }
    return null;
  }

  private String getReceiverNameFromPath(CompiledValue path) {
    if (path instanceof CompiledID) {
      return ((CompiledID) path).getId();
    } else if (path instanceof CompiledPath) {
      return getReceiverNameFromPath(path.getReceiver());
    } else if (path instanceof CompiledOperation) {
      return getReceiverNameFromPath(path.getReceiver());
    } else if (path instanceof CompiledIndexOperation) {
      return getReceiverNameFromPath(path.getReceiver());
    }
    return "";
  }



  /**
   * This will populate resultSet from both type of indexes, {@link CompactRangeIndex} and
   * {@link RangeIndex}.
   */
  void populateListForEquiJoin(List list, Object outerEntries, Object innerEntries,
      ExecutionContext context, Object key) throws FunctionDomainException, TypeMismatchException,
      NameResolutionException, QueryInvocationTargetException {

    Assert.assertTrue(outerEntries != null && innerEntries != null,
        "OuterEntries or InnerEntries must not be null");

    Object[][] values = new Object[2][];
    Iterator itr = null;
    int j = 0;

    while (j < 2) {
      boolean isRangeIndex = false;
      if (j == 0) {
        if (outerEntries instanceof MultiValuedMap) {
          itr = ((MultiValuedMap) outerEntries).entrySet().iterator();
          isRangeIndex = true;
        } else if (outerEntries instanceof CloseableIterator) {
          itr = (Iterator) outerEntries;
        }
      } else {
        if (innerEntries instanceof MultiValuedMap) {
          itr = ((MultiValuedMap) innerEntries).entrySet().iterator();
          isRangeIndex = true;
        } else if (innerEntries instanceof CloseableIterator) {
          itr = (Iterator) innerEntries;
        }
      }

      // extract the values from the RegionEntries
      List dummy = new ArrayList();
      RegionEntry re = null;
      IndexStoreEntry ie = null;
      Object val = null;
      Object entryVal = null;

      IndexInfo[] indexInfo = (IndexInfo[]) context.cacheGet(CompiledValue.INDEX_INFO);
      IndexInfo indInfo = indexInfo[j];

      while (itr.hasNext()) {
        if (isRangeIndex) {
          Map.Entry entry = (Map.Entry) itr.next();
          val = entry.getValue();
          if (val instanceof Collection) {
            entryVal = ((Iterable) val).iterator().next();
          } else {
            entryVal = val;
          }
          re = (RegionEntry) entry.getKey();
        } else {
          ie = (IndexStoreEntry) itr.next();
        }

        // Bug#41010: We need to verify if Inner and Outer Entries
        // are consistent with index key values.
        boolean ok = true;
        if (isRangeIndex) {
          if (re.isUpdateInProgress()) {
            ok = ((RangeIndex) indInfo._getIndex()).verifyEntryAndIndexValue(re, entryVal, context);
          }
        } else if (ie.isUpdateInProgress()) {
          ok = ((CompactRangeIndex) indInfo._getIndex()).verifyInnerAndOuterEntryValues(ie, context,
              indInfo, key);
        }
        if (ok) {
          if (isRangeIndex) {
            if (val instanceof Collection) {
              dummy.addAll((Collection) val);
            } else {
              dummy.add(val);
            }
          } else {
            if (IndexManager.IS_TEST_EXPANSION) {
              dummy.addAll(((CompactRangeIndex) indInfo._getIndex()).expandValue(context, key, null,
                  OQLLexerTokenTypes.TOK_EQ, -1, ie.getDeserializedValue()));
            } else {
              dummy.add(ie.getDeserializedValue());
            }
          }
        }
      }
      Object[] newValues = new Object[dummy.size()];
      dummy.toArray(newValues);
      values[j++] = newValues;
    }
    list.add(values);
  }

  /**
   * Sets the isIndexedPdxKeys flag indicating if all the keys in the index are Strings or
   * PdxStrings. Also sets another flag isIndexedPdxKeysFlagSet that indicates isIndexedPdxKeys has
   * been set/reset to avoid frequent calculation of map size
   */
  synchronized void setPdxStringFlag(Object key) {
    // For Null and Undefined keys do not set the isIndexedPdxKeysFlagSet flag
    if (isIndexedPdxKeysFlagSet || key == null || key == IndexManager.NULL
        || key == QueryService.UNDEFINED) {
      return;
    }
    if (!this.isIndexedPdxKeys) {
      if (key instanceof PdxString && this.region.getAttributes().getCompressor() == null) {
        this.isIndexedPdxKeys = true;
      }
    }
    this.isIndexedPdxKeysFlagSet = true;
  }

  /**
   * Converts Strings to PdxStrings and vice-versa based on the isIndexedPdxKeys flag
   *
   * @return PdxString or String based on isIndexedPdxKeys flag
   */
  Object getPdxStringForIndexedPdxKeys(Object key) {
    if (this.isIndexedPdxKeys) {
      if (key instanceof String) {
        return new PdxString((String) key);
      }
    } else if (key instanceof PdxString) {
      return key.toString();
    }
    return key;
  }

  boolean acquireIndexReadLockForRemove() {
    boolean success = this.removeIndexLock.readLock().tryLock();
    if (success) {
      this.internalIndexStats.incReadLockCount(1);
      if (logger.isDebugEnabled()) {
        logger.debug("Acquired read lock on index {}", this.getName());
      }
    }
    return success;
  }

  public void releaseIndexReadLockForRemove() {
    this.removeIndexLock.readLock().unlock();
    this.internalIndexStats.incReadLockCount(-1);
    if (logger.isDebugEnabled()) {
      logger.debug("Released read lock on index {}", this.getName());
    }
  }

  /**
   * This makes current thread wait until all query threads are done using it.
   */
  public void acquireIndexWriteLockForRemove() {
    final boolean isDebugEnabled = logger.isDebugEnabled();
    if (isDebugEnabled) {
      logger.debug("Acquiring write lock on Index {}", this.getName());
    }
    this.removeIndexLock.writeLock().lock();
    if (isDebugEnabled) {
      logger.debug("Acquired write lock on index {}", this.getName());
    }
  }

  public void releaseIndexWriteLockForRemove() {
    final boolean isDebugEnabled = logger.isDebugEnabled();
    if (isDebugEnabled) {
      logger.debug("Releasing write lock on Index {}", this.getName());
    }
    this.removeIndexLock.writeLock().unlock();
    if (isDebugEnabled) {
      logger.debug("Released write lock on Index {}", this.getName());
    }
  }

  public boolean isPopulated() {
    return this.isPopulated;
  }

  public void setPopulated(boolean isPopulated) {
    this.isPopulated = isPopulated;
  }

  boolean isIndexOnPdxKeys() {
    return isIndexedPdxKeys;
  }
}
