package org.apache.geode.distributed.internal.membership;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.runner.RunWith;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = "org.apache.geode.distributed.internal.membership.gms")
public class MembershipDependenciesJUnitTest {

  @ArchTest
  public static final ArchRule rule1 = classes().should()
      .onlyDependOnClassesThat(
          new DescribedPredicate<JavaClass>("Only reside in the membership package") {
            @Override
            public boolean apply(JavaClass input) {
              return input.getPackageName()
                  .startsWith("org.apache.geode.distributed.internal.membership")
                  || !input.getPackageName().startsWith("org.apache.geode");
            }
          });
}
