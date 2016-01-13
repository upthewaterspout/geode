package com.gemstone.gemfire.internal.cache;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;

import com.gemstone.gemfire.cache.Operation;
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

  @Test
  public void x() {
    InternalRegionArguments internalRegionArgs = null;
    LocalRegion owner = mock(LocalRegion.class);
    when(owner.isInitialized()).thenReturn(true);
    Attributes attrs = new Attributes();
    TestingRegionMap map = new TestingRegionMap(owner, attrs, internalRegionArgs);
    
    EntryEventImpl putEvent = mock(EntryEventImpl.class);
    when(putEvent.getKey()).thenReturn("key");
    when(putEvent.getOperation()).thenReturn(Operation.CREATE);
    when(putEvent.getLocalRegion()).thenReturn(owner);
    map.basicPut(putEvent, 0, false, false, null, false, false);
    assertNotNull(map.getEntry("key"));
    
    EntryEventImpl destroyEvent = mock(EntryEventImpl.class);
    when(destroyEvent.getKey()).thenReturn("key");
    when(destroyEvent.getOperation()).thenReturn(Operation.DESTROY);
    assertTrue(map.destroy(destroyEvent , false, false, false, false, null, false));
    assertEquals(null, map.getEntry("key"));
  }
}
