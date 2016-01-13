package com.gemstone.gemfire.internal.cache;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;

import com.gemstone.gemfire.internal.cache.RegionMap.Attributes;

public class TestingRegionMapJUnitTest {

  @Test
  public void destroyNonExistantKeyReturnsFalse() {
    InternalRegionArguments internalRegionArgs = null;
    Object owner = mock(LocalRegion.class);
    Attributes attrs = new Attributes();
    TestingRegionMap map = new TestingRegionMap(owner, attrs, internalRegionArgs);
    
    EntryEventImpl event = mock(EntryEventImpl.class);
    when(event.getKey()).thenReturn("key");
    assertFalse(map.destroy(event , false, false, false, false, null, false));
  }

}
