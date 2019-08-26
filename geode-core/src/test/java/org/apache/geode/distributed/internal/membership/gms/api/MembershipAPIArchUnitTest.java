package org.apache.geode.distributed.internal.membership.gms.api;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.runner.RunWith;

import org.apache.geode.distributed.DistributedMember;
import org.apache.geode.distributed.internal.DMStats;
import org.apache.geode.distributed.internal.DistributionConfig;
import org.apache.geode.distributed.internal.DistributionMessage;
import org.apache.geode.distributed.internal.membership.DistributedMembershipListener;
import org.apache.geode.distributed.internal.membership.InternalDistributedMember;
import org.apache.geode.distributed.internal.membership.InternalMembershipManager;
import org.apache.geode.distributed.internal.membership.MembershipView;
import org.apache.geode.distributed.internal.membership.gms.GMSMember;
import org.apache.geode.distributed.internal.membership.gms.MembershipManagerFactoryImpl;
import org.apache.geode.internal.admin.remote.RemoteTransportConfig;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = "org.apache.geode..")
public class MembershipAPIArchUnitTest {

  @ArchTest
  public static final ArchRule membershipAPIDoesntDependOnMembershipORCore = classes()
      .should()
      .onlyDependOnClassesThat(
          resideInAPackage("org.apache.geode.distributed.internal.membership.gms.api")
              .or(not(resideInAPackage("org.apache.geode..")))
              // this is allowed
              .or(type(MembershipManagerFactoryImpl.class))

              // to be extracted as Interfaces
              .or(type(InternalDistributedMember.class))
              .or(type(DistributedMembershipListener.class))
              .or(type(MembershipView.class))
              .or(type(GMSMember.class))
              .or(type(DistributedMember.class))
              .or(type(InternalDistributedMember[].class))
              .or(type(DistributionMessage.class))
              .or(type(InternalMembershipManager.class))
              .or(type(DistributionConfig.class))
              .or(type(RemoteTransportConfig.class))
              .or(type(DMStats.class)));


  @ArchIgnore
  @ArchTest
  public static final ArchRule gmsOnlyAccessedByAPILayer = layeredArchitecture()
      .layer("gms")
      .definedBy("org.apache.geode.distributed.internal.membership.gms..")
      .layer("api")
      .definedBy("org.apache.geode.distributed.internal.membership.gms.api")
      .whereLayer("gms")
      .mayOnlyBeAccessedByLayers("api");
}
