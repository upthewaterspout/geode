package org.apache.geode.internal.size;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.runner.RunWith;

import org.apache.geode.DataSerializable;
import org.apache.geode.DataSerializer;
import org.apache.geode.InternalGemFireError;
import org.apache.geode.cache.Declarable;
import org.apache.geode.cache.util.ObjectSizer;
import org.apache.geode.internal.Assert;
import org.apache.geode.internal.ClassPathLoader;
import org.apache.geode.internal.HeapDataOutputStream;
import org.apache.geode.internal.JvmSizeUtils;
import org.apache.geode.internal.serialization.Version;
import org.apache.geode.internal.util.concurrent.CopyOnWriteWeakHashMap;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = "org.apache.geode.internal.size..")
public class SizingArchUnitTest2 {
  @ArchTest
  public ArchRule sizeShouldNotDependOnCore = classes()
      .that()
      .resideInAPackage("org.apache.geode.internal.size..")
      .should()
      .onlyDependOnClassesThat(
              resideInAPackage("org.apache.geode.internal.size..")
              .or(not(resideInAPackage("org.apache.geode..")))

              //Can depend on geode-logging
              .or(resideInAPackage("org.apache.geode.logging.internal.log4j.api"))

              //Can depend on geode-unsafe
              .or(resideInAPackage("org.apache.geode.unsafe.internal.sun.misc"))

              .or(type(ObjectSizer.class))
              .or(type(DataSerializable.class))
              .or(type(DataSerializer.class))
              .or(type(HeapDataOutputStream.class))
              .or(type(Declarable.class))
              .or(type(Assert.class))
              .or(type(ClassPathLoader.class))
              .or(type(CopyOnWriteWeakHashMap.class))
              .or(type(JvmSizeUtils.class))
              .or(type(InternalGemFireError.class))
              .or(type(Version.class))
      );
}
