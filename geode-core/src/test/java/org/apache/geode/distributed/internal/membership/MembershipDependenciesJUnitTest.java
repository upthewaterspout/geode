package org.apache.geode.distributed.internal.membership;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.Ignore;
import org.junit.runner.RunWith;

/*
 Run manually for now.
 TODO: stop ignoring once we've eliminated all the violations yeah!
 */
@Ignore("Ignoring until all membership dependencies are cleaned up")
@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages="org.apache.geode.distributed.internal.membership..")
public class MembershipDependenciesJUnitTest {

  @ArchTest
  public static final ArchRule membershipDoesntDependOnCore = classes()
      .that()
      .resideInAPackage("org.apache.geode.distributed.internal.membership..")
      .should()
      .onlyDependOnClassesThat(
          resideInAPackage("org.apache.geode.distributed.internal.membership..")
              .or(not(resideInAPackage("org.apache.geode.."))));

}
