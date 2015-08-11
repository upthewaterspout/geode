package com.gemstone.gemfire.internal.cache;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collector;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.execute.FunctionAdapter;
import com.gemstone.gemfire.cache.execute.FunctionContext;
import com.gemstone.gemfire.cache.execute.FunctionService;
import com.gemstone.gemfire.cache.execute.RegionFunctionContext;
import com.gemstone.gemfire.cache.execute.ResultCollector;
import com.gemstone.gemfire.cache.partition.PartitionRegionHelper;

public class GFStreamPipeline<P> implements Stream<P>, Serializable {
  
  private transient Region source;
  private GFStreamPipeline previous;
  private Operation op;

  public GFStreamPipeline(Region source) {
    this.source = source;
  }
  
  public GFStreamPipeline(Region source, GFStreamPipeline<P> previous, Operation op) {
    this.source = source;
    this.previous = previous;
    this.op = op;
  }

  @Override
  public Iterator<P> iterator() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Spliterator<P> spliterator() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean isParallel() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public Stream<P> sequential() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Stream<P> parallel() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Stream<P> unordered() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Stream<P> onClose(Runnable closeHandler) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void close() {
    // TODO Auto-generated method stub
    
  }

  @Override
  public Stream<P> filter(Predicate<? super P> predicate) {
    return new GFStreamPipeline<P>(source, this, s -> s.filter(predicate));
  }

  @Override
  public <R> Stream<R> map(Function<? super P, ? extends R> mapper) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public IntStream mapToInt(ToIntFunction<? super P> mapper) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public LongStream mapToLong(ToLongFunction<? super P> mapper) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public DoubleStream mapToDouble(ToDoubleFunction<? super P> mapper) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public <R> Stream<R> flatMap(
      Function<? super P, ? extends Stream<? extends R>> mapper) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public IntStream flatMapToInt(
      Function<? super P, ? extends IntStream> mapper) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public LongStream flatMapToLong(
      Function<? super P, ? extends LongStream> mapper) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public DoubleStream flatMapToDouble(
      Function<? super P, ? extends DoubleStream> mapper) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Stream<P> distinct() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Stream<P> sorted() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Stream<P> sorted(Comparator<? super P> comparator) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Stream<P> peek(Consumer<? super P> action) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Stream<P> limit(long maxSize) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Stream<P> skip(long n) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void forEach(Consumer<? super P> action) {
    ResultCollector<?, ?> result = FunctionService.onRegion(source).withArgs(this).execute(new FunctionAdapter() {
      
      @Override
      public String getId() {
        return getClass().getName();
      }
      
      @Override
      public void execute(FunctionContext context) {
        RegionFunctionContext regionContext = (RegionFunctionContext) context;
        GFStreamPipeline<P> pipeline = (GFStreamPipeline<P>) regionContext.getArguments();
        
        Region dataSet = regionContext.getDataSet();
        if(PartitionRegionHelper.isPartitionedRegion(dataSet)) {
          dataSet = PartitionRegionHelper.getLocalDataForContext(regionContext);
        }
        Stream data = pipeline.invoke(dataSet.entrySet().stream());
        
        context.getResultSender().lastResult(data.toArray());
      }
    });
    
    List<P[]> results = (List<P[]>) result.getResult();
    results.stream().flatMap(a -> Arrays.asList(a).stream()).forEach(action);
    
  }

  @Override
  public void forEachOrdered(Consumer<? super P> action) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public Object[] toArray() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public <A> A[] toArray(IntFunction<A[]> generator) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public P reduce(P identity, BinaryOperator<P> accumulator) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Optional<P> reduce(BinaryOperator<P> accumulator) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public <U> U reduce(U identity, BiFunction<U, ? super P, U> accumulator,
      BinaryOperator<U> combiner) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public <R> R collect(Supplier<R> supplier,
      BiConsumer<R, ? super P> accumulator, BiConsumer<R, R> combiner) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public <R, A> R collect(Collector<? super P, A, R> collector) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Optional<P> min(Comparator<? super P> comparator) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Optional<P> max(Comparator<? super P> comparator) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public long count() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public boolean anyMatch(Predicate<? super P> predicate) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean allMatch(Predicate<? super P> predicate) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean noneMatch(Predicate<? super P> predicate) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public Optional<P> findFirst() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Optional<P> findAny() {
    // TODO Auto-generated method stub
    return null;
  }
  
  public Stream invoke(Stream in) {
    if(this.previous == null) {
      return in;
    } else {
      return this.op.apply(this.previous.invoke(in));
    }
  }
  
  public static interface Operation<IN, OUT> extends Serializable {
    Stream<IN> apply(Stream<OUT> stream);
  }

}
