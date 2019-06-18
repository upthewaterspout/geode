package org.apache.geode.distributed.internal.membership;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.Ignore;
import org.junit.runner.RunWith;

import org.apache.geode.DataSerializer;
import org.apache.geode.GemFireException;
import org.apache.geode.distributed.internal.DMStats;
import org.apache.geode.distributed.internal.DistributionConfig;
import org.apache.geode.distributed.internal.DistributionMessage;
import org.apache.geode.internal.DataSerializableFixedID;
import org.apache.geode.internal.logging.LogService;

/*
 * Run manually for now.
 * TODO: stop ignoring once we've eliminated all the violations yeah!
 */
@Ignore("Ignoring until all membership dependencies are cleaned up")
@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = "org.apache.geode.distributed.internal.membership..")
public class MembershipDependenciesJUnitTest {

  @ArchTest
  public static final ArchRule membershipDoesntDependOnCore = classes()
      .that()
      .resideInAPackage("org.apache.geode.distributed.internal.membership..")
      .and()
      .resideOutsideOfPackage("org.apache.geode.distributed.internal.membership.adapter..")
      .and()

      // GEODE-XXX: InternalDistributedMember needs to move out of the package
      .areNotAssignableFrom(InternalDistributedMember.class)


      .should()
      .onlyDependOnClassesThat(
          resideInAPackage("org.apache.geode.distributed.internal.membership..")

              .or(not(resideInAPackage("org.apache.geode..")))

              // GEODE-XXX: Create a new stats interface for membership
              .or(assignableTo(DMStats.class))

              // GEODE-XXX: Figure out what to do with exceptions
              .or(assignableTo(GemFireException.class))

              // GEODE-XXX: Serialization needs to become its own module
              .or(type(DataSerializer.class))
              .or(type(DataSerializableFixedID.class))

              // GEODE-XXX: Figure out what to do with messaging
              .or(assignableTo(DistributionMessage.class))

              // GEODE-XXX: Membership needs its own config object
              .or(type(DistributionConfig.class))

              // GEODE-XXX: Break depedency on geode logger
              .or(type(LogService.class))

  );

}
