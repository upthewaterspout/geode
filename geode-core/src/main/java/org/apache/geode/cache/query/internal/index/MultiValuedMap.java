package org.apache.geode.cache.query.internal.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.apache.geode.internal.cache.RegionEntry;

/**
 * A map which can contain multiple values for the same key.
 */
public class MultiValuedMap {

  private static final AtomicIntegerFieldUpdater<MultiValuedMap> atomicUpdater =
      AtomicIntegerFieldUpdater.newUpdater(MultiValuedMap.class, "numValues");

  private final boolean useList;
  private final Map<RegionEntry, Object> map;
  private volatile int numValues;

  public MultiValuedMap(Map map, boolean useList) {
    this.map = map;
    this.useList = useList;
  }

  /**
   * We do NOT use any locks here as every add is for a RegionEntry which is locked before coming
   * here. No two threads can be entering in this method together for a RegionEntry.
   */
  public void add(RegionEntry entry, Object value) {
    assert value != null;
    // Values must NOT be null and ConcurrentHashMap does not support null values.
    if (value == null) {
      return;
    }
    Object object = this.map.get(entry);
    if (object == null) {
      this.map.put(entry, value);
    } else if (object instanceof Collection) {
      Collection coll = (Collection) object;
      // If its a list query might get ConcurrentModificationException.
      // This can only happen for Null mapped or Undefined entries in a
      // RangeIndex. So we are synchronizing on ArrayList.
      if (this.isUseList()) {
        synchronized (coll) {
          coll.add(value);
        }
      } else {
        coll.add(value);
      }
    } else {
      Collection coll =
          this.isUseList() ? new ArrayList(2) : new IndexConcurrentHashSet(2, 0.75f, 1);
      coll.add(object);
      coll.add(value);
      this.map.put(entry, coll);
    }
    atomicUpdater.incrementAndGet(this);
  }

  public void addAll(RegionEntry entry, Collection values) {
    Object object = this.map.get(entry);
    if (object == null) {
      Collection coll = this.isUseList() ? new ArrayList(values.size())
          : new IndexConcurrentHashSet(values.size(), 0.75f, 1);
      coll.addAll(values);
      this.map.put(entry, coll);
      atomicUpdater.addAndGet(this, values.size());
    } else if (object instanceof Collection) {
      Collection coll = (Collection) object;
      // If its a list query might get ConcurrentModificationException.
      // This can only happen for Null mapped or Undefined entries in a
      // RangeIndex. So we are synchronizing on ArrayList.
      if (this.isUseList()) {
        synchronized (coll) {
          coll.addAll(values);
        }
      } else {
        coll.addAll(values);
      }
    } else {
      Collection coll = this.isUseList() ? new ArrayList(values.size() + 1)
          : new IndexConcurrentHashSet(values.size() + 1, 0.75f, 1);
      coll.addAll(values);
      coll.add(object);
      this.map.put(entry, coll);
    }
    atomicUpdater.addAndGet(this, values.size());
  }

  /**
   * This method will return either
   * <li>null - if there is no mapping for the entry</li>
   * <li>a single object - if there is only one mapping</li>
   * <li>a collection - if there are multiple mappings for the entry</li>
   */
  public Object get(RegionEntry entry) {
    return this.map.get(entry);
  }

  /**
   * We do NOT use any locks here as every remove is for a RegionEntry which is locked before
   * coming here. No two threads can be entering in this method together for a RegionEntry.
   */
  public void remove(RegionEntry entry, Object value) {
    Object object = this.map.get(entry);
    if (object == null)
      return;
    if (object instanceof Collection) {
      Collection coll = (Collection) object;
      boolean removed;
      // If its a list query might get ConcurrentModificationException.
      // This can only happen for Null mapped or Undefined entries in a
      // RangeIndex. So we are synchronizing on ArrayList.
      if (this.isUseList()) {
        synchronized (coll) {
          removed = coll.remove(value);
        }
      } else {
        removed = coll.remove(value);
      }
      if (removed) {
        if (coll.size() == 0) {
          this.map.remove(entry);
        }
        atomicUpdater.decrementAndGet(this);
      }
    } else {
      if (object.equals(value)) {
        this.map.remove(entry);
      }
      atomicUpdater.decrementAndGet(this);
    }
  }

  public Object remove(RegionEntry entry) {
    Object retVal = this.map.remove(entry);
    if (retVal != null) {
      atomicUpdater.addAndGet(this,
          retVal instanceof Collection ? -((Collection) retVal).size() : -1);
    }
    return retVal;
  }

  int getNumValues(RegionEntry entry) {
    Object object = this.map.get(entry);
    if (object == null)
      return 0;
    if (object instanceof Collection) {
      Collection coll = (Collection) object;
      return coll.size();
    } else {
      return 1;
    }
  }

  public int getNumValues() {
    return atomicUpdater.get(this);
  }

  public int getNumEntries() {
    return this.map.keySet().size();
  }

  public boolean containsEntry(RegionEntry entry) {
    return this.map.containsKey(entry);
  }

  public boolean containsValue(Object value) {
    throw new RuntimeException(
        "Not yet implemented");
  }

  public void clear() {
    this.map.clear();
    atomicUpdater.set(this, 0);
  }

  /**
   * Returns the entries set for this map. For the possible types
   * of values in this entry set, see {@link #get(RegionEntry)}
   */
  public Set<Map.Entry<RegionEntry, Object>> entrySet() {
    return this.map.entrySet();
  }

  /**
   * This replaces a key's value along with updating the numValues correctly.
   */
  public void replace(RegionEntry entry, Object values) {
    int numOldValues = getNumValues(entry);
    this.map.put(entry, values);
    atomicUpdater.addAndGet(this,
        (values instanceof Collection ? ((Collection) values).size() : 1) - numOldValues);
  }

  public boolean isUseList() {
    return useList;
  }

  public Set<RegionEntry> keySet() {
    return map.keySet();
  }

  public int size() {
    return map.size();
  }

  public Collection<Object> values() {
    return map.values();
  }
}
