package com.gemstone.gemfire.internal.stats50;

import com.gemstone.gemfire.StatisticsType;
import com.gemstone.gemfire.internal.StatisticsManager;
import com.gemstone.gemfire.test.junit.categories.UnitTest;

import org.junit.experimental.categories.Category;

@Category(UnitTest.class)
public class Atomic50StatisticsImplJUnitTest {

  public void testOverloadedSamplers() {
    final StatisticsType type;
    final String textId = "";
    final long numbericId = 0;
    final long uniqueId = 0;
    final StatisticsManager system;
    Atomic50StatisticsImpl stats = new Atomic50StatisticsImpl(type, textId, numbericId, uniqueId, system);
    stats.incLong(0, 5);
    inc((long) 5);

  }
}