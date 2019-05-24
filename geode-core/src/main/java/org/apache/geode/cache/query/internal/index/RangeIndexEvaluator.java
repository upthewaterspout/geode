package org.apache.geode.cache.query.internal.index;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.query.AmbiguousNameException;
import org.apache.geode.cache.query.FunctionDomainException;
import org.apache.geode.cache.query.NameResolutionException;
import org.apache.geode.cache.query.QueryInvocationTargetException;
import org.apache.geode.cache.query.TypeMismatchException;
import org.apache.geode.cache.query.internal.CompiledIteratorDef;
import org.apache.geode.cache.query.internal.CompiledValue;
import org.apache.geode.cache.query.internal.ExecutionContext;
import org.apache.geode.cache.query.internal.QRegion;
import org.apache.geode.cache.query.internal.QueryMonitor;
import org.apache.geode.cache.query.internal.RuntimeIterator;
import org.apache.geode.cache.query.internal.StructImpl;
import org.apache.geode.cache.query.internal.Support;
import org.apache.geode.cache.query.internal.types.StructTypeImpl;
import org.apache.geode.cache.query.types.ObjectType;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.internal.cache.NonTXEntry;
import org.apache.geode.internal.cache.RegionEntry;

public class RangeIndexEvaluator implements IndexedExpressionEvaluator {
  private AbstractIndex index;
  private final InternalCache cache;

  private List fromIterators = null;

  private CompiledValue indexedExpr = null;

  private final String[] canonicalIterNames;

  private ObjectType indexResultSetType = null;

  private Map dependencyGraph = null;

  /**
   * The boolean if true indicates that the 0th iterator is on entries . If the 0th iterator is on
   * collection of Region.Entry objects, then the RegionEntry object used in Index data objects is
   * obtained directly from its corresponding Region.Entry object. However if the 0th iterator is
   * not on entries then the boolean is false. In this case the additional projection attribute
   * gives us the original value of the iterator while the Region.Entry object is obtained from
   * 0th iterator. It is possible to have index being created on a Region Entry itself , instead
   * of a Region. A Map operator( Compiled Index Operator) used with Region enables, us to create
   * such indexes. In such case the 0th iterator, even if it represents a collection of Objects
   * which are not Region.Entry objects, still the boolean remains true, as the Entry object can
   * be easily obtained from the 0th iterator. In this case, the additional projection attribute s
   * not null as it is used to evaluate the Entry object from the 0th iterator.
   */
  private boolean isFirstItrOnEntry = false;

  /** The boolean if true indicates that the 0th iterator is on keys. */
  private boolean isFirstItrOnKey = false;

  /**
   * List of modified iterators, not null only when the boolean isFirstItrOnEntry is false.
   */
  private List indexInitIterators = null;

  /**
   * The additional Projection attribute representing the value of the original 0th iterator. If
   * the isFirstItrOnEntry is false, then it is not null. However if the isFirstItrOnEntry is true
   * but & still this attribute is not null, this indicates that the 0th iterator is derived using
   * an individual entry thru Map operator on the Region.
   */
  private CompiledValue additionalProj = null;

  /** This is not null iff the boolean isFirstItrOnEntry is false. */
  private CompiledValue modifiedIndexExpr = null;

  private ObjectType addnlProjType = null;

  private int initEntriesUpdated = 0;

  private boolean hasInitOccurredOnce = false;

  private ExecutionContext initContext = null;

  private int iteratorSize = -1;

  private Region rgn = null;

  /** Creates a new instance of IMQEvaluator */
  RangeIndexEvaluator(AbstractIndex index, IndexCreationHelper helper) {
    this.index = index;
    this.cache = helper.getCache();
    this.fromIterators = helper.getIterators();
    this.indexedExpr = helper.getCompiledIndexedExpression();
    this.rgn = helper.getRegion();
    // The modified iterators for optimizing Index creation
    this.isFirstItrOnEntry = ((FunctionalIndexCreationHelper) helper).isFirstIteratorRegionEntry;
    this.isFirstItrOnKey = ((FunctionalIndexCreationHelper) helper).isFirstIteratorRegionKey;
    this.additionalProj = ((FunctionalIndexCreationHelper) helper).additionalProj;
    Object[] params1 = {new QRegion(this.rgn, false)};
    this.initContext = new ExecutionContext(params1, this.cache);
    this.canonicalIterNames = ((FunctionalIndexCreationHelper) helper).canonicalizedIteratorNames;
    if (this.isFirstItrOnEntry) {
      this.indexInitIterators = this.fromIterators;
    } else {
      this.indexInitIterators = ((FunctionalIndexCreationHelper) helper).indexInitIterators;
      this.modifiedIndexExpr = ((FunctionalIndexCreationHelper) helper).modifiedIndexExpr;
      this.addnlProjType = ((FunctionalIndexCreationHelper) helper).addnlProjType;
    }
    this.iteratorSize = this.indexInitIterators.size();
  }

  @Override
  public String getIndexedExpression() {
    return index.getCanonicalizedIndexedExpression();
  }

  @Override
  public String getProjectionAttributes() {
    return index.getCanonicalizedProjectionAttributes();
  }

  @Override
  public String getFromClause() {
    return index.getCanonicalizedFromClause();
  }

  @Override
  public void expansion(List expandedResults, Object lowerBoundKey, Object upperBoundKey,
      int lowerBoundOperator, int upperBoundOperator, Object value) throws IMQException {
    // no-op
  }

  public void evaluate(RegionEntry target, IndexUpdateOperation updateOperation)
      throws IMQException {
    DummyQRegion dQRegion = new DummyQRegion(this.rgn);
    dQRegion.setEntry(target);
    Object[] params = {dQRegion};
    ExecutionContext context = new ExecutionContext(params, this.cache);
    context.newScope(IndexCreationHelper.INDEX_QUERY_SCOPE_ID);

    try {
      boolean computeDependency = true;
      if (this.dependencyGraph != null) {
        context.setDependencyGraph(this.dependencyGraph);
        computeDependency = false;
      }

      for (int i = 0; i < this.iteratorSize; i++) {
        CompiledIteratorDef iterDef = (CompiledIteratorDef) this.fromIterators.get(i);
        // Compute the dependency only once. The call to methods of this
        // class are thread safe as for update lock on Index is taken .
        if (computeDependency) {
          iterDef.computeDependencies(context);
        }
        RuntimeIterator rIter = iterDef.getRuntimeIterator(context);
        context.addToIndependentRuntimeItrMapForIndexCreation(iterDef);
        context.bindIterator(rIter);
      }

      // Save the dependency graph for future updates.
      if (this.dependencyGraph == null) {
        this.dependencyGraph = context.getDependencyGraph();
      }

      Support.Assert(this.indexResultSetType != null,
          "IMQEvaluator::evaluate:The StrcutType should have been initialized during index creation");

      doNestedIterations(0, context, updateOperation);
    } catch (IMQException imqe) {
      throw imqe;
    } catch (Exception e) {
      throw new IMQException(e);
    } finally {
      context.popScope();
    }
  }

  /**
   * This function is used for creating Index data at the start
   */
  public void initializeIndex(boolean loadEntries,
      IndexUpdateOperation updateOperation) throws IMQException {
    this.initEntriesUpdated = 0;
    try {
      // Since an index initialization can happen multiple times for a given region, due to clear
      // operation, we are using hardcoded scope ID of 1 , as otherwise if obtained from
      // ExecutionContext object, it will get incremented on very index initialization
      this.initContext.newScope(1);
      for (int i = 0; i < this.iteratorSize; i++) {
        CompiledIteratorDef iterDef = (CompiledIteratorDef) this.indexInitIterators.get(i);
        RuntimeIterator rIter = null;
        if (!this.hasInitOccurredOnce) {
          iterDef.computeDependencies(this.initContext);
          rIter = iterDef.getRuntimeIterator(this.initContext);
          this.initContext.addToIndependentRuntimeItrMapForIndexCreation(iterDef);
        }
        if (rIter == null) {
          rIter = iterDef.getRuntimeIterator(this.initContext);
        }
        this.initContext.bindIterator(rIter);
      }
      this.hasInitOccurredOnce = true;
      if (this.indexResultSetType == null) {
        this.indexResultSetType = createIndexResultSetType();
      }
      if (loadEntries) {
        doNestedIterationsForIndexInit(0, this.initContext.getCurrentIterators(), updateOperation);
      }
    } catch (IMQException imqe) {
      throw imqe;
    } catch (Exception e) {
      throw new IMQException(e);
    } finally {
      this.initContext.popScope();
    }
  }

  private void doNestedIterationsForIndexInit(int level, List runtimeIterators,
      IndexUpdateOperation updateOperation)
      throws TypeMismatchException, AmbiguousNameException, FunctionDomainException,
      NameResolutionException, QueryInvocationTargetException, IMQException {
    if (level == 1) {
      ++this.initEntriesUpdated;
    }
    if (level == this.iteratorSize) {
      applyProjectionForIndexInit(runtimeIterators, updateOperation);
    } else {
      RuntimeIterator rIter = (RuntimeIterator) runtimeIterators.get(level);
      Collection collection = rIter.evaluateCollection(this.initContext);
      if (collection == null) {
        return;
      }
      for (Object aCollection : collection) {
        rIter.setCurrent(aCollection);
        doNestedIterationsForIndexInit(level + 1, runtimeIterators, updateOperation);
      }
    }
  }

  /**
   * This function is used to obtain Index data at the time of index creation. Each element of the
   * List is an Object Array of size 3. The 0th element of Object Array stores the value of Index
   * Expression. The 1st element of ObjectArray contains the RegionEntry object ( If the boolean
   * isFirstItrOnEntry is false, then the 0th iterator will give us the Region.Entry object which
   * can be used to obtain the underlying RegionEntry object. If the boolean is true & additional
   * projection attribute is not null, then the Region.Entry object can be obtained by evaluating
   * the additional projection attribute. If the boolean isFirstItrOnEntry is true & additional
   * projection attribute is null, then the 0th iterator itself will evaluate to Region.Entry
   * Object.
   * <p>
   * The 2nd element of Object Array contains the Struct object ( tuple) created. If the boolean
   * isFirstItrOnEntry is false, then the first attribute of the Struct object is obtained by
   * evaluating the additional projection attribute.
   */
  private void applyProjectionForIndexInit(List currrentRuntimeIters,
      IndexUpdateOperation updateOperation)
      throws FunctionDomainException, TypeMismatchException, NameResolutionException,
      QueryInvocationTargetException, IMQException {

    if (QueryMonitor.isLowMemory()) {
      throw new IMQException(
          "Index creation canceled due to low memory");
    }

    NonTXEntry temp;

    // Evaluate NonTXEntry for index on entries or additional projections
    // on Entry or just entry value.
    if (this.isFirstItrOnEntry && this.additionalProj != null) {
      temp = (NonTXEntry) this.additionalProj.evaluate(this.initContext);
    } else {
      temp = (NonTXEntry) ((RuntimeIterator) currrentRuntimeIters.get(0))
          .evaluate(this.initContext);
    }

    RegionEntry re = temp.getRegionEntry();
    Object indxResultSet;

    if (this.iteratorSize == 1) {
      indxResultSet = this.isFirstItrOnEntry
          ? this.additionalProj == null ? temp
              : ((RuntimeIterator) currrentRuntimeIters.get(0)).evaluate(this.initContext)
          : this.additionalProj.evaluate(this.initContext);
    } else {
      Object[] tuple = new Object[this.iteratorSize];
      int i = this.isFirstItrOnEntry ? 0 : 1;
      for (; i < this.iteratorSize; i++) {
        RuntimeIterator iter = (RuntimeIterator) currrentRuntimeIters.get(i);
        tuple[i] = iter.evaluate(this.initContext);
      }
      if (!this.isFirstItrOnEntry) {
        tuple[0] = this.additionalProj.evaluate(this.initContext);
      }
      Support.Assert(this.indexResultSetType instanceof StructTypeImpl,
          "The Index ResultType should have been an instance of StructTypeImpl rather than ObjectTypeImpl. The indxeResultType is "
              + this.indexResultSetType);
      indxResultSet = new StructImpl((StructTypeImpl) this.indexResultSetType, tuple);
    }

    // Key must be evaluated after indexResultSet evaluation is done as Entry might be getting
    // destroyed and so if value is UNDEFINED, key will definitely will be UNDEFINED.
    Object indexKey = this.isFirstItrOnEntry ? this.indexedExpr.evaluate(this.initContext)
        : this.modifiedIndexExpr.evaluate(this.initContext);
    // based on the first key convert the rest to PdxString or String
    if (!index.isIndexedPdxKeysFlagSet) {
      index.setPdxStringFlag(indexKey);
    }
    indexKey = index.getPdxStringForIndexedPdxKeys(indexKey);
    updateOperation.add(indexKey, indxResultSet, re);
  }

  private void doNestedIterations(int level, ExecutionContext context,
      IndexUpdateOperation updateOperation)
      throws TypeMismatchException, AmbiguousNameException, FunctionDomainException,
      NameResolutionException, QueryInvocationTargetException, IMQException {

    List iterList = context.getCurrentIterators();
    if (level == this.iteratorSize) {
      applyProjection(context, updateOperation);
    } else {
      RuntimeIterator rIter = (RuntimeIterator) iterList.get(level);
      Collection collection = rIter.evaluateCollection(context);
      if (collection == null) {
        return;
      }
      for (Object aCollection : collection) {
        rIter.setCurrent(aCollection);
        doNestedIterations(level + 1, context, updateOperation);
      }
    }
  }

  private void applyProjection(ExecutionContext context,
      IndexUpdateOperation updateOperation)
      throws FunctionDomainException, TypeMismatchException, NameResolutionException,
      QueryInvocationTargetException, IMQException {

    List currrentRuntimeIters = context.getCurrentIterators();
    Object indexKey = this.indexedExpr.evaluate(context);
    // based on the first key convert the rest to PdxString or String
    if (!index.isIndexedPdxKeysFlagSet) {
      index.setPdxStringFlag(indexKey);
    }
    indexKey = index.getPdxStringForIndexedPdxKeys(indexKey);
    Object indxResultSet;

    if (this.iteratorSize == 1) {
      RuntimeIterator iter = (RuntimeIterator) currrentRuntimeIters.get(0);
      indxResultSet = iter.evaluate(context);
    } else {
      Object tuple[] = new Object[this.iteratorSize];
      for (int i = 0; i < this.iteratorSize; i++) {
        RuntimeIterator iter = (RuntimeIterator) currrentRuntimeIters.get(i);
        tuple[i] = iter.evaluate(context);
      }
      Support.Assert(this.indexResultSetType instanceof StructTypeImpl,
          "The Index ResultType should have been an instance of StructTypeImpl rather than ObjectTypeImpl. The indxeResultType is "
              + this.indexResultSetType);
      indxResultSet = new StructImpl((StructTypeImpl) this.indexResultSetType, tuple);
    }

    // Keep Entry value in fly until all keys are evaluated
    RegionEntry entry = ((DummyQRegion) context.getBindArgument(1)).getEntry();
    updateOperation.add(indexKey, indxResultSet, entry);
  }

  /**
   * The struct type calculation is modified if the 0th iterator is modified to make it dependent
   * on Entry
   */
  private ObjectType createIndexResultSetType() {
    List currentIterators = this.initContext.getCurrentIterators();
    int len = currentIterators.size();
    ObjectType[] fieldTypes = new ObjectType[len];
    int start = this.isFirstItrOnEntry ? 0 : 1;
    for (; start < len; start++) {
      RuntimeIterator iter = (RuntimeIterator) currentIterators.get(start);
      fieldTypes[start] = iter.getElementType();
    }
    if (!this.isFirstItrOnEntry) {
      fieldTypes[0] = this.addnlProjType;
    }
    return len == 1 ? fieldTypes[0] : new StructTypeImpl(this.canonicalIterNames, fieldTypes);
  }

  @Override
  public ObjectType getIndexResultSetType() {
    return this.indexResultSetType;
  }

  boolean isFirstItrOnEntry() {
    return this.isFirstItrOnEntry;
  }

  boolean isFirstItrOnKey() {
    return this.isFirstItrOnKey;
  }

  public interface IndexUpdateOperation {
    void add(Object indexKey, Object value, RegionEntry entry) throws IMQException;
  }
}
