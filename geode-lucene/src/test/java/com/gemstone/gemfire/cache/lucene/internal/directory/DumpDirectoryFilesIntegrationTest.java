package com.gemstone.gemfire.cache.lucene.internal.directory;

import static com.gemstone.gemfire.cache.lucene.test.LuceneTestUtilities.*;
import static org.junit.Assert.*;

import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionShortcut;
import com.gemstone.gemfire.cache.execute.FunctionService;
import com.gemstone.gemfire.cache.execute.ResultCollector;
import com.gemstone.gemfire.cache.lucene.LuceneIndex;
import com.gemstone.gemfire.cache.lucene.LuceneIntegrationTest;
import com.gemstone.gemfire.cache.lucene.LuceneQuery;
import com.gemstone.gemfire.cache.lucene.test.TestObject;

import org.junit.Test;

public class DumpDirectoryFilesIntegrationTest extends LuceneIntegrationTest {

  @Test
  public void dumpDirectoryFilesDumpsLuceneIndexFiles() throws Exception {
    luceneService.createIndex(INDEX_NAME, REGION_NAME, "title", "description");

    Region region = createRegion(REGION_NAME, RegionShortcut.PARTITION);
    region.put("object-1", new TestObject("title 1", "hello world"));
    region.put("object-2", new TestObject("title 2", "this will not match"));
    region.put("object-3", new TestObject("title 3", "hello world"));
    region.put("object-4", new TestObject("hello world", "hello world"));

    LuceneIndex index = luceneService.getIndex(INDEX_NAME, REGION_NAME);
    index.waitUntilFlushed(60000);
    ResultCollector resultCollector= FunctionService.onRegion(region).execute(new DumpDirectoryFiles().getId());
    resultCollector.getResult();
  }

}