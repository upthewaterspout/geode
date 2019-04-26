package org.apache.geode.internal.security;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.query.internal.index.IndexStore;
import org.apache.geode.internal.cache.InternalCache;

/**
 * Static utility methods for post processing region entries and values.
 */
public class PostProcessing {

  /**
   * Return a post processed view of the entry set of the region. Values in this
   * entry set are lazily post processed by the security post processor.
   *
   * If post processing is not required, this just returns the entry set.
   */
  public static <K> Collection<Map.Entry<K, Object>> entrySet(Region<K, ?> region,
      Object principal) {
    SecurityService securityService = getSecurityService(region);

    if (!securityService.needPostProcess()) {
      return (Collection) region.entrySet();
    }
    String fullPath = region.getFullPath();

    Function<Map.Entry<K, ?>, Map.Entry<K, Object>> transformation =
        entry -> transformEntry(securityService, entry, fullPath, principal);
    return new TransformingCollection<>(transformation, region.entrySet());
  }

  /**
   * Return a post processed view of the values of the region. Values in this
   * collection are lazily post processed by the security post processor.
   *
   * If post processing is not required, this just returns the entry set.
   */
  public static Collection<Object> values(Region<?, ?> region, Object principal) {
    SecurityService securityService = getSecurityService(region);

    if (!securityService.needPostProcess()) {
      return (Collection<Object>) region.values();
    }

    String fullPath = region.getFullPath();

    Function<Map.Entry<?, ?>, Object> transformation =
        entry -> transformValue(securityService, entry, fullPath, principal);
    return new TransformingCollection<>(transformation, region.entrySet());
  }

  /**
   * Return a post processed value from an index entry.
   */
  public static Object getPostProcessedValue(Region region, IndexStore.IndexStoreEntry indexEntry,
      Object principal) {
    SecurityService securityService = getSecurityService(region);

    if (!securityService.needPostProcess()) {
      return indexEntry.getDeserializedValue();
    }

    return securityService.postProcess(principal, region.getFullPath(),
        indexEntry.getDeserializedRegionKey(),
        indexEntry.getDeserializedValue(), false);
  }

  /**
   * Post process a region value, if required
   */
  public static Object getPostProcessedValue(Region region, Object key, Object value,
      Object principal) {
    SecurityService securityService = getSecurityService(region);

    if (!securityService.needPostProcess()) {
      return value;
    }

    return securityService.postProcess(principal, region.getFullPath(),
        key,
        value, false);
  }

  public static boolean needsPostProcessing(Region region) {
    return getSecurityService(region).needPostProcess();

  }

  /**
   * Post process the value of a region entry if required. If no post processing is required, return
   * the original
   * entry.
   */
  public static Map.Entry getPostProcessedEntry(Region region, Map.Entry entry, Object principal) {
    SecurityService securityService = getSecurityService(region);

    if (!securityService.needPostProcess()) {
      return entry;
    }

    return transformEntry(securityService, entry, region.getFullPath(), principal);
  }

  private static SecurityService getSecurityService(Region<?, ?> region) {
    InternalCache cache = (InternalCache) region.getRegionService();
    return cache.getSecurityService();
  }

  private static <K, V> Map.Entry<K, Object> transformEntry(SecurityService securityService,
      Map.Entry<K, V> entry,
      String regionPath, Object principal) {
    Object value = transformValue(securityService, entry, regionPath, principal);
    K key = entry.getKey();
    return Pair.of(key, value);
  }

  private static <K, V> Object transformValue(SecurityService securityService,
      Map.Entry<K, V> entry,
      String regionPath, Object principal) {
    K key = entry.getKey();
    String fullPath = regionPath;
    V originalValue = entry.getValue();
    return securityService.postProcess(principal, fullPath, key, originalValue, false);
  }

  private PostProcessing() {

  }


}
