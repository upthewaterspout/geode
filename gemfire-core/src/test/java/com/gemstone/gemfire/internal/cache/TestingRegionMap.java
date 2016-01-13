package com.gemstone.gemfire.internal.cache;

import com.gemstone.gemfire.internal.cache.RegionMap.Attributes;

public class TestingRegionMap extends AbstractRegionMap {

  protected TestingRegionMap(Object owner, Attributes attr,
      InternalRegionArguments internalRegionArgs) {
    super(internalRegionArgs);
    initialize(owner, attr, internalRegionArgs, false);
  }

  @Override
  protected void checkOwnerValidity(EntryEventImpl event, LocalRegion owner) {
    //do nothing
  }

  @Override
  protected boolean retainForConcurrency(EntryEventImpl event,
      LocalRegion owner, boolean haveTombstone) {
    return false;
  }
  
  protected boolean mustDistribute(EntryEventImpl event) {
    return false;
  }
  
  

  
}
