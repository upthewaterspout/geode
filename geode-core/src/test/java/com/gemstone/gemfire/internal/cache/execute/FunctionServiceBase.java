/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gemstone.gemfire.internal.cache.execute;

import static com.gemstone.gemfire.test.dunit.Wait.pause;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.gemstone.gemfire.cache.CacheClosedException;
import com.gemstone.gemfire.cache.execute.Execution;
import com.gemstone.gemfire.cache.execute.Function;
import com.gemstone.gemfire.cache.execute.FunctionContext;
import com.gemstone.gemfire.cache.execute.FunctionException;
import com.gemstone.gemfire.cache.execute.ResultCollector;
import com.gemstone.gemfire.distributed.DistributedMember;
import com.gemstone.gemfire.distributed.internal.InternalDistributedSystem;
import com.gemstone.gemfire.distributed.internal.membership.InternalDistributedMember;
import com.gemstone.gemfire.test.dunit.Host;
import com.gemstone.gemfire.test.dunit.cache.internal.JUnit4CacheTestCase;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/*
 * Base class for tests of FunctionService that are agnostic to the
 * type of Execution that they are running on. The goal is to completely
 * cover all common behavior of sending results and sending exceptions
 * here and have them run with all topologies in child classes.
 */
public abstract class FunctionServiceBase extends JUnit4CacheTestCase {

  @Rule
  public transient ExpectedException thrown = ExpectedException.none();

  protected transient CustomCollector customCollector;

  @Before
  public void createCollector() {
    this.customCollector = new CustomCollector();
  }

  /**
   * Return the execution used to execute functions for this
   * test. Subclasses should override this to provide a specific
   * execution, for example onMember.
   */
  public abstract Execution getExecution();

  /**
   * Return the number of members the function is expected
   * to execute on
   */
  public abstract int numberOfExecutions();

  @Test
  public void defaultCollectorReturnsSingleResult() {
    final Host host = Host.getHost(0);

    ResultCollector rc = getExecution().execute((context) -> {context.getResultSender().lastResult("done");});
    List<String> results = (List<String>) rc.getResult();
    assertEquals(numberOfExecutions(), results.size());
    results.stream().forEach(element -> assertEquals("done", element));
  }

  @Test()
  public void defaultCollectorThrowsExceptionAfterFunctionThrowsIllegalState() {
    final Host host = Host.getHost(0);

    ResultCollector rc = getExecution().execute((context) -> {throw new IllegalStateException();});
    thrown.expect(FunctionException.class);
    thrown.expectCause(isA(IllegalStateException.class));
    final Object result = rc.getResult();
  }

  @Test()
  public void defaultCollectorThrowsExceptionAfterFunctionThrowsFunctionException() {
    final Host host = Host.getHost(0);

    ResultCollector rc = getExecution().execute((context) -> {throw new FunctionException();});

    thrown.expect(FunctionException.class);
    final Object result = rc.getResult();
  }

  /**
   * Tests what happens if a function returns an exception as a result. This
   * is kind a weird, but it seems that the default collector will just throw it
   * as an exception
   */
  @Test()
  public void defaultCollectorThrowsExceptionAfterFunctionReturnsIllegalStateException() {
    final Host host = Host.getHost(0);

    ResultCollector rc = getExecution().execute((context) -> {context.getResultSender().lastResult(new IllegalStateException());});

    thrown.expect(FunctionException.class);
    thrown.expectCause(isA(IllegalStateException.class));
    final Object result = rc.getResult();
  }

  @Test()
  public void defaultCollectorThrowsExceptionAfterFunctionReturnsFunctionException() {
    final Host host = Host.getHost(0);

    ResultCollector rc = getExecution().execute((context) -> {context.getResultSender().lastResult(new FunctionException());});
    thrown.expect(FunctionException.class);
    thrown.expectCause(is((Throwable) null));
    final Object result = rc.getResult();
  }

  @Test()
  public void defaultCollectorThrowsExceptionAfterFunctionReturnsIllegalStateExceptionAsIntermediateResult() {
    final Host host = Host.getHost(0);

    ResultCollector rc = getExecution().execute((context) -> {
        context.getResultSender().sendResult(new IllegalStateException());
        context.getResultSender().lastResult("done");
      });
    thrown.expect(FunctionException.class);
    thrown.expectCause(isA(IllegalStateException.class));
    final Object result = rc.getResult();
  }

  @Test()
  public void defaultCollectorThrowsExceptionAfterFunctionReturnsFunctionExceptionAsIntermediateResult() {
    final Host host = Host.getHost(0);

    ResultCollector rc = getExecution().execute((context) -> {
        context.getResultSender().sendResult(new FunctionException());
        context.getResultSender().lastResult("done");
    });
    thrown.expect(FunctionException.class);
    thrown.expectCause(is((Throwable) null));
    final Object result = rc.getResult();
  }

  @Test
  public void defaultCollectorReturnsResultOfSendException() {
    final Host host = Host.getHost(0);

    ResultCollector rc = getExecution().execute((context) -> {
      context.getResultSender().sendException(new IllegalStateException());
    });
    final List<Object> result = (List<Object>) rc.getResult();
    assertEquals(numberOfExecutions(), result.size());
    result.stream().forEach(element -> assertEquals(IllegalStateException.class, element.getClass()));
  }

  @Test
  public void defaultCollectorReturnsResultOfSendFunctionException() {
    final Host host = Host.getHost(0);

    ResultCollector rc = getExecution().execute((context) -> {
      context.getResultSender().sendException(new FunctionException());
    });
    final List<Object> result = (List<Object>) rc.getResult();
    assertEquals(numberOfExecutions(), result.size());
    result.stream().forEach(element -> assertEquals(FunctionException.class, element.getClass()));
  }

  @Test
  public void customCollectorDoesNotSeeExceptionFunctionThrowsIllegalState() {
    final Host host = Host.getHost(0);

    ResultCollector rc = getExecution().withCollector(customCollector).execute((context) -> {throw new IllegalStateException();});
    try {
      rc.getResult();
      fail("should have received an exception");
    } catch (FunctionException expected) {}

    Assert.assertEquals(0, customCollector.getResult().size());
  }

  @Test
  public void customCollectorDoesNotSeeExceptionFunctionThrowsFunctionException() {
    final Host host = Host.getHost(0);

    ResultCollector rc = getExecution().withCollector(customCollector).execute((context) -> {throw new FunctionException();});
    try {
      rc.getResult();
      fail("should have received an exception");
    } catch (FunctionException expected) {}

    Assert.assertEquals(0, customCollector.getResult().size());
  }

  @Test
  public void customCollectorDoesNotSeeExceptionAfterFunctionReturnsIllegalStateException() {
    final Host host = Host.getHost(0);

    ResultCollector rc = getExecution().execute((context) -> {context.getResultSender().lastResult(new IllegalStateException());});
    try {
      rc.getResult();
      fail("should have received an exception");
    } catch (FunctionException expected) {}
    Assert.assertEquals(0, customCollector.getResult().size());
  }

  @Test
  public void customCollectorDoesNotSeeExceptionAfterFunctionReturnsIllegalStateExceptionAsIntermediateResult() {
    final Host host = Host.getHost(0);

    ResultCollector rc = getExecution().execute((context) -> {
      context.getResultSender().sendResult(new IllegalStateException());
      context.getResultSender().lastResult("done");
    });
    try {
      rc.getResult();
      fail("should have received an exception");
    } catch (FunctionException expected) {}
    Assert.assertEquals(0, customCollector.getResult().size());
  }

  @Test
  public void customCollectorReturnsResultOfSendException() {
    final Host host = Host.getHost(0);

    ResultCollector rc = getExecution().withCollector(customCollector).execute((context) -> {
      context.getResultSender().sendException(new IllegalStateException());
    });
    final List<Object> result = (List<Object>) rc.getResult();
    assertEquals(numberOfExecutions(), result.size());
    result.stream().forEach(element -> assertEquals(IllegalStateException.class, element.getClass()));
    assertEquals(result, customCollector.getResult());
  }

  @Test
  public void customCollectorReturnsResultOfSendFunctionException() {
    final Host host = Host.getHost(0);

    ResultCollector rc = getExecution().withCollector(customCollector).execute((context) -> {
      context.getResultSender().sendException(new FunctionException());
    });
    final List<Object> result = (List<Object>) rc.getResult();
    assertEquals(numberOfExecutions(), result.size());
    result.stream().forEach(element -> assertEquals(FunctionException.class, element.getClass()));
    assertEquals(result, customCollector.getResult());
  }

  /**
   * Test that a custom result collector will still receive all partial
   * results from other members when one member fails
   */
  @Test
  public void nonHAFunctionResultCollectorIsPassedPartialResultsAfterCloseCache() {
    List<InternalDistributedMember> members = getAllMembers();

    InternalDistributedMember firstMember = members.iterator().next();

    //Execute a function which will close the cache on one member.
    try {
      ResultCollector rc = getExecution()
        .withCollector(customCollector)
        .execute(new CacheClosingNonHAFunction(firstMember));
      rc.getResult();
      fail("Should have thrown an exception");
    } catch(Exception expected) {
      expected.printStackTrace();
      //do nothing
    }
    members.remove(firstMember);
    assertEquals(members, customCollector.getResult());
    assertEquals(numberOfExecutions() - 1, customCollector.getResult().size());
  }

  protected List<InternalDistributedMember> getAllMembers() {
    //Get a list of all of the members
    ResultCollector rs = getExecution().execute(functionContext -> {
      functionContext.getResultSender().lastResult(InternalDistributedSystem.getAnyInstance().getDistributedMember());
    });
    return (List<InternalDistributedMember>) rs.getResult();
  }

  public static class CustomCollector implements ResultCollector<Object, List<Object>> {
    private ArrayList<Object> results = new ArrayList<Object>();

    @Override
    public List<Object> getResult() throws FunctionException {
      return results;
    }

    @Override
    public List<Object> getResult(final long timeout, final TimeUnit unit)
      throws FunctionException, InterruptedException
    {
      return results;
    }

    @Override
    public void addResult(final DistributedMember memberID, final Object resultOfSingleExecution) {
      results.add(resultOfSingleExecution);
    }

    @Override
    public void endResults() {
    }

    @Override
    public void clearResults() {
      results.clear();
    }
  }

  /**
   * A function which will close the cache if the given member matches
   * the member executing this function
   */
  private class CacheClosingNonHAFunction implements Function {

    private final InternalDistributedMember member;

    public CacheClosingNonHAFunction(final InternalDistributedMember member) {
      this.member = member;
    }

    @Override
    public void execute(FunctionContext context) {
      final InternalDistributedMember myId = InternalDistributedSystem.getAnyInstance().getDistributedMember();
      if (myId.equals(member)) {
        getCache().close();
        throw new CacheClosedException();
      }
      pause(1000);
      context.getResultSender().lastResult(myId);
    }

    @Override
    public boolean isHA() {
      return false;
    }
  }

}
