package com.gemstone.gemfire.internal.cache;

import org.apache.logging.log4j.Logger;

import com.gemstone.gemfire.cache.EntryNotFoundException;
import com.gemstone.gemfire.cache.query.internal.index.IndexManager;
import com.gemstone.gemfire.internal.cache.versions.ConcurrentCacheModificationException;
import com.gemstone.gemfire.internal.cache.versions.VersionTag;
import com.gemstone.gemfire.internal.logging.LogService;
import com.gemstone.gemfire.internal.sequencelog.EntryLogger;

public class EntryDestroyer {
  private static final Logger logger = LogService.getLogger();

  private AbstractRegionMap map;
  private EntryEventImpl event;
  private boolean inTokenMode;
  private boolean duringRI;
  private boolean cacheWrite;
  private boolean isEviction;
  private Object expectedOldValue;
  private boolean removeRecoveredEntry;
  private boolean retry = true;
  private LocalRegion owner;
  private boolean sqlfIndexLocked;

  private boolean opCompleted;

  private boolean doPart3;

  private boolean doUnlock;

  private RegionEntry tombstone;

  private boolean haveTombstone;

  private RegionEntry re;

  private IndexManager oqlIndexManager;

  private boolean retainForConcurrency;

  private RegionEntry oldRe;

  private boolean destroyed;

  public EntryDestroyer(AbstractRegionMap map, EntryEventImpl event,
      boolean inTokenMode, boolean duringRI, boolean cacheWrite,
      boolean isEviction, Object expectedOldValue,
      boolean removeRecoveredEntry) {
    this.map = map;
    this.event = event;
    this.inTokenMode = inTokenMode;
    this.duringRI = duringRI;
    this.cacheWrite = cacheWrite;
    this.isEviction = isEviction;
    this.expectedOldValue = expectedOldValue;
    this.removeRecoveredEntry = removeRecoveredEntry;
    owner = map._getOwner();
  }

  public boolean run() {
    map.checkOwnerValidity(event, owner);

    RETRY_LOOP: while (retry) {
      retry = false;

      lockSqlfIndex();
      opCompleted = false;
      doPart3 = false;

      doUnlock = true;
      map.lockForCacheModification(owner, event);
      try {

        re = map.getOrCreateRegionEntry(owner, event, Token.REMOVED_PHASE1,
            null, true, true);
        tombstone = null;
        haveTombstone = false;
        /*
         * Execute the test hook runnable inline (not threaded) if it is not
         * null.
         */
        if (null != map.testHookRunnableFor48182) {
          map.testHookRunnableFor48182.run();
        }

        try {
          map.logTombstoneCount(event, inTokenMode, duringRI, isEviction, owner,
              re);
          if (event.isFromRILocalDestroy()) {
            // for RI local-destroy we don't want to keep tombstones.
            // In order to simplify things we just set this recovery
            // flag to true to force the entry to be removed
            removeRecoveredEntry = true;
          }
          // the logic in this method is already very involved, and adding
          // tombstone
          // permutations to (re != null) greatly complicates it. So, we check
          // for a tombstone here and, if found, pretend for a bit that the
          // entry is null
          if (re != null && re.isTombstone() && !removeRecoveredEntry) {
            tombstone = re;
            haveTombstone = true;
            re = null;
          }
          oqlIndexManager = owner.getIndexManager();
          if (re == null) {
            retainForConcurrency = map.retainForConcurrency(event, owner,
                haveTombstone);
            if (inTokenMode || retainForConcurrency) {
              RegionEntry newRe = map.getEntryFactory().createEntry(owner,
                  event.getKey(), Token.REMOVED_PHASE1);
              // Fix for Bug #44431. We do NOT want to update the region and
              // wait
              // later for index INIT as region.clear() can cause inconsistency
              // if
              // happened in parallel as it also does index INIT.
              if (oqlIndexManager != null) {
                oqlIndexManager.waitForIndexInit();
              }
              try {
                synchronized (newRe) {
                  oldRe = map.putEntryIfAbsent(event.getKey(), newRe);
                  while (!opCompleted && oldRe != null) {
                    synchronized (oldRe) {
                      if (oldRe.isRemovedPhase2()) {
                        oldRe = map.putEntryIfAbsent(event.getKey(), newRe);
                        if (oldRe != null) {
                          owner.getCachePerfStats().incRetries();
                        }
                      } else {
                        event.setRegionEntry(oldRe);

                        // Last transaction related eviction check. This should
                        // prevent
                        // transaction conflict (caused by eviction) when the
                        // entry
                        // is being added to transaction state.
                        if (isEviction) {
                          if (!map.confirmEvictionDestroy(oldRe)
                              || (owner.getEvictionCriteria() != null && !owner
                                  .getEvictionCriteria().doEvict(event))) {
                            opCompleted = false;
                            return opCompleted;
                          }
                        }
                        try {
                          destroyed = map.destroyEntry(oldRe, event,
                              inTokenMode, cacheWrite, expectedOldValue, false,
                              removeRecoveredEntry);
                          if (destroyed) {
                            if (retainForConcurrency) {
                              owner.basicDestroyBeforeRemoval(oldRe, event);
                            }
                            owner.basicDestroyPart2(oldRe, event, inTokenMode,
                                false /* conflict with clear */, duringRI,
                                true);
                            // if (!oldRe.isTombstone() || isEviction) {
                            map.lruEntryDestroy(oldRe);
                            // } else { // tombstone
                            // lruEntryUpdate(oldRe);
                            // lruUpdateCallback = true;
                            // }
                            doPart3 = true;
                          }
                        } catch (RegionClearedException rce) {
                          // region cleared implies entry is no longer there
                          // so must throw exception if expecting a particular
                          // old value
                          // if (expectedOldValue != null) {
                          // throw new EntryNotFoundException("entry not found
                          // with expected value");
                          // }

                          // Ignore. The exception will ensure that we do not
                          // update
                          // the LRU List
                          owner.basicDestroyPart2(oldRe, event, inTokenMode,
                              true/* conflict with clear */, duringRI, true);
                          doPart3 = true;
                        } catch (ConcurrentCacheModificationException ccme) {
                          VersionTag tag = event.getVersionTag();
                          if (tag != null && tag.isTimeStampUpdated()) {
                            // Notify gateways of new time-stamp.
                            owner.notifyTimestampsToGateways(event);
                          }
                          throw ccme;
                        }
                        re = oldRe;
                        opCompleted = true;
                      }
                    } // synchronized oldRe
                  } // while
                  if (!opCompleted) {
                    // The following try has a finally that cleans up the newRe.
                    // This is only needed if newRe was added to the map which
                    // only
                    // happens if we didn't get completed with oldRe in the
                    // above while loop.
                    try { // bug #42228 - leaving "removed" entries in the cache
                      re = newRe;
                      event.setRegionEntry(newRe);

                      try {
                        // if concurrency checks are enabled, destroy will
                        // set the version tag
                        if (isEviction) {
                          opCompleted = false;
                          return opCompleted;
                        }
                        opCompleted = map.destroyEntry(newRe, event,
                            inTokenMode, cacheWrite, expectedOldValue, true,
                            removeRecoveredEntry);
                        if (opCompleted) {
                          // This is a new entry that was created because we are
                          // in
                          // token mode or are accepting a destroy operation by
                          // adding
                          // a tombstone. There is no oldValue, so we don't need
                          // to
                          // call updateSizeOnRemove
                          // owner.recordEvent(event);
                          event.setIsRedestroyedEntry(true); // native clients
                                                             // need to know if
                                                             // the entry didn't
                                                             // exist
                          if (retainForConcurrency) {
                            owner.basicDestroyBeforeRemoval(oldRe, event);
                          }
                          owner.basicDestroyPart2(newRe, event, inTokenMode,
                              false /* conflict with clear */, duringRI, true);
                          doPart3 = true;
                        }
                      } catch (RegionClearedException rce) {
                        // region cleared implies entry is no longer there
                        // so must throw exception if expecting a particular
                        // old value
                        // if (expectedOldValue != null) {
                        // throw new EntryNotFoundException("entry not found
                        // with expected value");
                        // }

                        // Ignore. The exception will ensure that we do not
                        // update
                        // the LRU List
                        opCompleted = true;
                        EntryLogger.logDestroy(event);
                        // owner.recordEvent(event, newRe);
                        owner.basicDestroyPart2(newRe, event, inTokenMode,
                            true /* conflict with clear */, duringRI, true);
                        doPart3 = true;
                      } catch (ConcurrentCacheModificationException ccme) {
                        VersionTag tag = event.getVersionTag();
                        if (tag != null && tag.isTimeStampUpdated()) {
                          // Notify gateways of new time-stamp.
                          owner.notifyTimestampsToGateways(event);
                        }
                        throw ccme;
                      }
                      // Note no need for LRU work since the entry is destroyed
                      // and will be removed when gii completes
                    } finally { // bug #42228
                      if (!opCompleted
                          && !haveTombstone /*
                                             * to fix bug 51583 do this for all
                                             * operations
                                             */ ) {

                        // owner.getLogWriterI18n().warning(LocalizedStrings.DEBUG,
                        // "BRUCE: removing incomplete entry");
                        map.removeEntry(event.getKey(), newRe, false);
                      }
                      if (!opCompleted && isEviction) {
                        map.removeEntry(event.getKey(), newRe, false);
                      }
                    }
                  } // !opCompleted
                } // synchronized newRe
              } finally {
                if (oqlIndexManager != null) {
                  oqlIndexManager.countDownIndexUpdaters();
                }
              }
            } // inTokenMode or tombstone creation
            else {
              if (!isEviction || owner.concurrencyChecksEnabled) {
                // The following ensures that there is not a concurrent
                // operation
                // on the entry and leaves behind a tombstone if
                // concurrencyChecksEnabled.
                // It fixes bug #32467 by propagating the destroy to the server
                // even though
                // the entry isn't in the client
                RegionEntry newRe = haveTombstone ? tombstone
                    : map.getEntryFactory().createEntry(owner, event.getKey(),
                        Token.REMOVED_PHASE1);
                synchronized (newRe) {
                  if (haveTombstone && !tombstone.isTombstone()) {
                    // we have to check this again under synchronization since
                    // it may have changed
                    retry = true;
                    // retryEntry = tombstone; // leave this in place for
                    // debugging
                    continue RETRY_LOOP;
                  }
                  re = (RegionEntry) map._getMap().putIfAbsent(event.getKey(),
                      newRe);
                  if (re != null && re != tombstone) {
                    // concurrent change - try again
                    retry = true;
                    // retryEntry = tombstone; // leave this in place for
                    // debugging
                    continue RETRY_LOOP;
                  } else if (!isEviction) {
                    boolean throwex = false;
                    EntryNotFoundException ex = null;
                    try {
                      if (!cacheWrite) {
                        throwex = true;
                      } else {
                        try {
                          if (!removeRecoveredEntry) {
                            throwex = !owner.bridgeWriteBeforeDestroy(event,
                                expectedOldValue);
                          }
                        } catch (EntryNotFoundException e) {
                          throwex = true;
                          ex = e;
                        }
                      }
                      if (throwex) {
                        if (map.mustDistribute(event)) { // or if this is a WAN
                                                         // event that has been
                                                         // applied in another
                                                         // system
                          // we must distribute these since they will update the
                          // version information in peers
                          if (logger.isDebugEnabled()) {
                            logger.debug(
                                "ARM.destroy is allowing wan/client destroy of {} to continue",
                                event.getKey());
                          }
                          throwex = false;
                          event.setIsRedestroyedEntry(true);
                          // Distribution of this op happens on re and re might
                          // me null here before
                          // distributing this destroy op.
                          if (re == null) {
                            re = newRe;
                          }
                          doPart3 = true;
                        }
                      }
                      if (throwex) {
                        if (ex == null) {
                          // Fix for 48182, check cache state and/or region
                          // state before sending entry not found.
                          // this is from the server and any exceptions will
                          // propogate to the client
                          owner.checkEntryNotFound(event.getKey());
                        } else {
                          throw ex;
                        }
                      }
                    } finally {
                      // either remove the entry or leave a tombstone
                      try {
                        if (!event.isOriginRemote()
                            && event.getVersionTag() != null
                            && owner.concurrencyChecksEnabled) {
                          // this shouldn't fail since we just created the
                          // entry.
                          // it will either generate a tag or apply a server's
                          // version tag
                          map.processVersionTag(newRe, event);
                          if (doPart3) {
                            owner.generateAndSetVersionTag(event, newRe);
                          }
                          try {
                            owner.recordEvent(event);
                            newRe.makeTombstone(owner, event.getVersionTag());
                          } catch (RegionClearedException e) {
                            // that's okay - when writing a tombstone into a
                            // disk, the
                            // region has been cleared (including this
                            // tombstone)
                          }
                          opCompleted = true;
                          // lruEntryCreate(newRe);
                        } else if (!haveTombstone) {
                          try {
                            assert newRe != tombstone;
                            newRe.setValue(owner, Token.REMOVED_PHASE2);
                            map.removeEntry(event.getKey(), newRe, false);
                          } catch (RegionClearedException e) {
                            // that's okay - we just need to remove the new
                            // entry
                          }
                        } else if (event.getVersionTag() != null) { // haveTombstone
                                                                    // - update
                                                                    // the
                                                                    // tombstone
                                                                    // version
                                                                    // info
                          map.processVersionTag(tombstone, event);
                          if (doPart3) {
                            owner.generateAndSetVersionTag(event, newRe);
                          }
                          // This is not conflict, we need to persist the
                          // tombstone again with new version tag
                          try {
                            tombstone.setValue(owner, Token.TOMBSTONE);
                          } catch (RegionClearedException e) {
                            // that's okay - when writing a tombstone into a
                            // disk, the
                            // region has been cleared (including this
                            // tombstone)
                          }
                          owner.recordEvent(event);
                          owner.rescheduleTombstone(tombstone,
                              event.getVersionTag());
                          owner.basicDestroyPart2(tombstone, event, inTokenMode,
                              true /* conflict with clear */, duringRI, true);
                          opCompleted = true;
                        }
                      } catch (ConcurrentCacheModificationException ccme) {
                        VersionTag tag = event.getVersionTag();
                        if (tag != null && tag.isTimeStampUpdated()) {
                          // Notify gateways of new time-stamp.
                          owner.notifyTimestampsToGateways(event);
                        }
                        throw ccme;
                      }
                    }
                  }
                } // synchronized(newRe)
              }
            }
          } // no current entry
          else { // current entry exists
            if (oqlIndexManager != null) {
              oqlIndexManager.waitForIndexInit();
            }
            try {
              synchronized (re) {
                // if the entry is a tombstone and the event is from a peer or a
                // client
                // then we allow the operation to be performed so that we can
                // update the
                // version stamp. Otherwise we would retain an old version stamp
                // and may allow
                // an operation that is older than the destroy() to be applied
                // to the cache
                // Bug 45170: If removeRecoveredEntry, we treat tombstone as
                // regular entry to be deleted
                boolean createTombstoneForConflictChecks = (owner.concurrencyChecksEnabled
                    && (event.isOriginRemote() || event.getContext() != null
                        || removeRecoveredEntry));
                if (!re.isRemoved() || createTombstoneForConflictChecks) {
                  if (re.isRemovedPhase2()) {
                    retry = true;
                    continue RETRY_LOOP;
                  }
                  if (!event.isOriginRemote()
                      && event.getOperation().isExpiration()) {
                    // If this expiration started locally then only do it if the
                    // RE is not being used by a tx.
                    if (re.isInUseByTransaction()) {
                      opCompleted = false;
                      return opCompleted;
                    }
                  }
                  event.setRegionEntry(re);

                  // See comment above about eviction checks
                  if (isEviction) {
                    assert expectedOldValue == null;
                    if (!map.confirmEvictionDestroy(re)
                        || (owner.getEvictionCriteria() != null
                            && !owner.getEvictionCriteria().doEvict(event))) {
                      opCompleted = false;
                      return opCompleted;
                    }
                  }

                  boolean removed = false;
                  try {
                    opCompleted = map.destroyEntry(re, event, inTokenMode,
                        cacheWrite, expectedOldValue, false,
                        removeRecoveredEntry);
                    if (opCompleted) {
                      // It is very, very important for Partitioned Regions to
                      // keep
                      // the entry in the map until after distribution occurs so
                      // that other
                      // threads performing a create on this entry wait until
                      // the destroy
                      // distribution is finished.
                      // keeping backup copies consistent. Fix for bug 35906.
                      // -- mthomas 07/02/2007 <-- how about that date, kinda
                      // cool eh?
                      owner.basicDestroyBeforeRemoval(re, event);

                      // do this before basicDestroyPart2 to fix bug 31786
                      if (!inTokenMode) {
                        if (re.getVersionStamp() == null) {
                          re.removePhase2();
                          map.removeEntry(event.getKey(), re, true, event,
                              owner, map.getIndexUpdater());
                          removed = true;
                        }
                      }
                      if (inTokenMode && !duringRI) {
                        event.inhibitCacheListenerNotification(true);
                      }
                      doPart3 = true;
                      owner.basicDestroyPart2(re, event, inTokenMode,
                          false /* conflict with clear */, duringRI, true);
                      // if (!re.isTombstone() || isEviction) {
                      map.lruEntryDestroy(re);
                      // } else {
                      // lruEntryUpdate(re);
                      // lruUpdateCallback = true;
                      // }
                    } else {
                      if (!inTokenMode) {
                        EntryLogger.logDestroy(event);
                        owner.recordEvent(event);
                        if (re.getVersionStamp() == null) {
                          re.removePhase2();
                          map.removeEntry(event.getKey(), re, true, event,
                              owner, map.getIndexUpdater());
                          map.lruEntryDestroy(re);
                        } else {
                          if (re.isTombstone()) {
                            // the entry is already a tombstone, but we're
                            // destroying it
                            // again, so we need to reschedule the tombstone's
                            // expiration
                            if (event.isOriginRemote()) {
                              owner.rescheduleTombstone(re,
                                  re.getVersionStamp().asVersionTag());
                            }
                          }
                        }
                        map.lruEntryDestroy(re);
                        opCompleted = true;
                      }
                    }
                  } catch (RegionClearedException rce) {
                    // Ignore. The exception will ensure that we do not update
                    // the LRU List
                    opCompleted = true;
                    owner.recordEvent(event);
                    if (inTokenMode && !duringRI) {
                      event.inhibitCacheListenerNotification(true);
                    }
                    owner.basicDestroyPart2(re, event, inTokenMode,
                        true /* conflict with clear */, duringRI, true);
                    doPart3 = true;
                  } finally {
                    if (re.isRemoved() && !re.isTombstone()) {
                      if (!removed) {
                        map.removeEntry(event.getKey(), re, true, event, owner,
                            map.getIndexUpdater());
                      }
                    }
                  }
                } // !isRemoved
                else { // already removed
                  if (owner.isHDFSReadWriteRegion() && re.isRemovedPhase2()) {
                    // For HDFS region there may be a race with eviction
                    // so retry the operation. fixes bug 49150
                    retry = true;
                    continue RETRY_LOOP;
                  }
                  if (re.isTombstone() && event.getVersionTag() != null) {
                    // if we're dealing with a tombstone and this is a remote
                    // event
                    // (e.g., from cache client update thread) we need to update
                    // the tombstone's version information
                    // TODO use destroyEntry() here
                    map.processVersionTag(re, event);
                    try {
                      re.makeTombstone(owner, event.getVersionTag());
                    } catch (RegionClearedException e) {
                      // that's okay - when writing a tombstone into a disk, the
                      // region has been cleared (including this tombstone)
                    }
                  }
                  if (expectedOldValue != null) {
                    // if re is removed then there is no old value, so return
                    // false
                    return false;
                  }

                  if (!inTokenMode && !isEviction) {
                    owner.checkEntryNotFound(event.getKey());
                  }
                  // if (isEviction && re.isTombstone()) {
                  // owner.unscheduleTombstone(re);
                  // removeTombstone(re, re.getVersionStamp().getEntryVersion(),
                  // true);
                  // }
                }
              } // synchronized re
            } catch (ConcurrentCacheModificationException ccme) {
              VersionTag tag = event.getVersionTag();
              if (tag != null && tag.isTimeStampUpdated()) {
                // Notify gateways of new time-stamp.
                owner.notifyTimestampsToGateways(event);
              }
              throw ccme;
            } finally {
              if (oqlIndexManager != null) {
                oqlIndexManager.countDownIndexUpdaters();
              }
            }
            // No need to call lruUpdateCallback since the only lru action
            // we may have taken was lruEntryDestroy. This fixes bug 31759.

          } // current entry exists
          if (opCompleted) {
            EntryLogger.logDestroy(event);
          }
          return opCompleted;
        } finally {
          map.releaseCacheModificationLock(owner, event);
          doUnlock = false;

          try {
            // release the SQLF index lock, if acquired
            if (sqlfIndexLocked) {
              map.getIndexUpdater().unlockForIndexGII();
            }
            // If concurrency conflict is there and event contains gateway
            // version tag then
            // do NOT distribute.
            if (event.isConcurrencyConflict() && (event.getVersionTag() != null
                && event.getVersionTag().isGatewayTag())) {
              doPart3 = false;
            }
            // distribution and listener notification
            if (doPart3) {
              owner.basicDestroyPart3(re, event, inTokenMode, duringRI, true,
                  expectedOldValue);
            }
            // if (lruUpdateCallback) {
            // lruUpdateCallback();
            // }
          } finally {
            if (opCompleted) {
              if (re != null) {
                owner.cancelExpiryTask(re);
              } else if (tombstone != null) {
                owner.cancelExpiryTask(tombstone);
              }
            }
          }
        }

      } finally { // failsafe on the read lock...see comment above
        if (doUnlock) {
          map.releaseCacheModificationLock(owner, event);
        }
      }
    } // retry loop
    return false;

  }

  private void lockSqlfIndex() {
    sqlfIndexLocked = false;
    if (map.getIndexUpdater() != null) {
      // take read lock for SQLF index initializations if required
      sqlfIndexLocked = map.getIndexUpdater().lockForIndexGII();
    }
  }

}
