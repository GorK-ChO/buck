/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.jvm.java;

import static com.facebook.buck.jvm.common.ResourceValidator.validateResources;

import com.facebook.buck.maven.AetherUtil;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.Flavored;
import com.facebook.buck.model.HasTests;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRuleType;
import com.facebook.buck.rules.BuildRules;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.Hint;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePaths;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.infer.annotation.SuppressFieldNotInitialized;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;

import java.nio.file.Path;

public class JavaLibraryDescription implements Description<JavaLibraryDescription.Arg>, Flavored {

  public static final BuildRuleType TYPE = BuildRuleType.of("java_library");
  public static final ImmutableSet<Flavor> SUPPORTED_FLAVORS = ImmutableSet.of(
      JavaLibrary.SRC_JAR,
      JavaLibrary.JAVADOC,
      JavaLibrary.MAVEN_JAR);

  @VisibleForTesting
  final JavacOptions defaultOptions;

  public JavaLibraryDescription(JavacOptions defaultOptions) {
    this.defaultOptions = defaultOptions;
  }

  @Override
  public BuildRuleType getBuildRuleType() {
    return TYPE;
  }

  @Override
  public boolean hasFlavors(ImmutableSet<Flavor> flavors) {
    return SUPPORTED_FLAVORS.containsAll(flavors);
  }

  @Override
  public Arg createUnpopulatedConstructorArg() {
    return new Arg();
  }

  @Override
  public <A extends Arg> BuildRule createBuildRule(
      TargetGraph targetGraph,
      BuildRuleParams params,
      final BuildRuleResolver resolver,
      A args) {
    SourcePathResolver pathResolver = new SourcePathResolver(resolver);
    final BuildTarget target = params.getBuildTarget();

    // Having the default target is Really Useful. Get it.
    BuildTarget unflavored = BuildTarget.of(target.getUnflavoredBuildTarget());
    if (target.equals(unflavored)) {
      return createJavaLibrary(params, resolver, pathResolver, args);
    }

    Optional<BuildRule> optionalBaseLibrary = resolver.getRuleOptional(unflavored);
    BuildRule baseLibrary;
    if (!optionalBaseLibrary.isPresent()) {
      baseLibrary = resolver.addToIndex(
          createBuildRule(
              targetGraph,
              params.copyWithBuildTarget(unflavored),
              resolver,
              args));
    } else {
      baseLibrary = optionalBaseLibrary.get();
    }

    RuleGatherer gatherer;
    if (target.getFlavors().contains(JavaLibrary.MAVEN_JAR)) {
      gatherer = RuleGatherer.MAVEN_JAR;
    } else {
      gatherer = RuleGatherer.SINGLE_JAR;
    }

    if (target.getFlavors().contains(JavaLibrary.JAVADOC)) {
      return new Javadoc(
          baseLibrary,
          params,
          pathResolver,
          args.mavenCoords.transform(
              new Function<String, String>() {
                @Override
                public String apply(String input) {
                  return AetherUtil.addClassifier(input, AetherUtil.CLASSIFIER_JAVADOC);
                }
              }),
          args.mavenPomTemplate.transform(pathResolver.getAbsolutePathFunction()),
          gatherer);
    }

    if (target.getFlavors().contains(JavaLibrary.SRC_JAR)) {
      return new JavaSourceJar(
        params,
          pathResolver,
          baseLibrary,
          gatherer,
          args.mavenPomTemplate.transform(pathResolver.getAbsolutePathFunction()),
          args.mavenCoords.transform(
              new Function<String, String>() {
                @Override
                public String apply(String input) {
                  return AetherUtil.addClassifier(input, AetherUtil.CLASSIFIER_SOURCES);
                }
              }));
    }

    if (target.getFlavors().contains(JavaLibrary.MAVEN_JAR)) {
      return MavenUberJar.create(
          (JavaLibrary) baseLibrary,
          params.copyWithExtraDeps(Suppliers.ofInstance(ImmutableSortedSet.of(baseLibrary))),
          pathResolver,
          args.mavenCoords.transform(
              new Function<String, String>() {
                @Override
                public String apply(String input) {
                  return AetherUtil.addClassifier(input, "");
                }
              }),
          args.mavenPomTemplate);
    }

    throw new HumanReadableException("Unrecognized target flavor: %s", target);

//    // We know that the flavour we're being asked to create is valid, since the check is done when
//    // creating the action graph from the target graph.
//
//    ImmutableSortedSet<Flavor> flavors = target.getFlavors();
//    BuildRuleParams paramsWithMavenFlavor = null;
//    if (flavors.contains(JavaLibrary.MAVEN_JAR)) {
//      paramsWithMavenFlavor = params;
//
//      // Maven rules will depend upon their vanilla versions, so the latter have to be constructed
//      // without the maven flavor to prevent output-path conflict
//      params = params.copyWithBuildTarget(
//          params.getBuildTarget().withoutFlavors(ImmutableSet.of(JavaLibrary.MAVEN_JAR)));
//    }
//
//    if (flavors.contains(JavaLibrary.SRC_JAR)) {
//      args.mavenCoords = args.mavenCoords.transform(
//          new Function<String, String>() {
//            @Override
//            public String apply(String input) {
//              return AetherUtil.addClassifier(input, AetherUtil.CLASSIFIER_SOURCES);
//            }
//          });
//
//      if (!flavors.contains(JavaLibrary.MAVEN_JAR)) {
//        return new JavaSourceJar(
//            params,
//            pathResolver,
//            args.srcs.get(),
//            args.mavenCoords);
//      } else {
//        return MavenUberJar.SourceJar.create(
//            Preconditions.checkNotNull(paramsWithMavenFlavor),
//            pathResolver,
//            args.srcs.get(),
//            args.mavenCoords,
//            args.mavenPomTemplate.transform(pathResolver.getAbsolutePathFunction()));
//      }
//    }
//
//    if (flavors.contains(JavaLibrary.JAVADOC)) {
//      // The javadoc jar needs the compiled jar to work from too.
//      BuildRule library = null;
//      BuildTarget unflavored = BuildTarget.of(target.getUnflavoredBuildTarget());
//      Optional<BuildRule> ruleOptional = resolver.getRuleOptional(unflavored);
//      if (ruleOptional.isPresent()) {
//        library = ruleOptional.get();
//      } else {
//        library = createBuildRule(
//            targetGraph,
//            params.copyWithBuildTarget(unflavored),
//            resolver,
//            args);
//      }
//
//      if (!flavors.contains(JavaLibrary.MAVEN_JAR)) {
//        return new Javadoc(
//            params.appendExtraDeps(Collections.singleton(library)),
//            pathResolver,
//            args.mavenCoords,
//            args.mavenPomTemplate.transform(pathResolver.getAbsolutePathFunction()),
//            RuleGatherer.SINGLE_JAR);
//      } else {
//        paramsWithMavenFlavor =
//            paramsWithMavenFlavor.appendExtraDeps(Collections.singleton(library));
//
//        args.mavenCoords = args.mavenCoords.transform(
//            new Function<String, String>() {
//              @Override
//              public String apply(String input) {
//                return AetherUtil.addClassifier(input, AetherUtil.CLASSIFIER_JAVADOC);
//              }
//            });
//
//        // We need the default java library too.
//        return new Javadoc(
//            Preconditions.checkNotNull(paramsWithMavenFlavor)
//                .appendExtraDeps(Collections.singleton(library)),
//            pathResolver,
//            args.mavenCoords,
//            args.mavenPomTemplate.transform(pathResolver.getAbsolutePathFunction()),
//            RuleGatherer.MAVEN_JAR);
//      }
//    }
//
//    JavacOptions javacOptions = JavacOptionsFactory.create(
//        defaultOptions,
//        params,
//        resolver,
//        pathResolver,
//        args
//    );
//
//    BuildTarget abiJarTarget = params.getBuildTarget().withAppendedFlavors(CalculateAbi.FLAVOR);
//
//    ImmutableSortedSet<BuildRule> exportedDeps = resolver.getAllRules(args.exportedDeps.get());
//
//    DefaultJavaLibrary newJavaLibrary = new DefaultJavaLibrary(
//        params.appendExtraDeps(
//            Iterables.concat(
//                BuildRules.getExportedRules(
//                    Iterables.concat(
//                        params.getDeclaredDeps().get(),
//                        exportedDeps,
//                        resolver.getAllRules(args.providedDeps.get()))),
//                pathResolver.filterBuildRuleInputs(
//                    javacOptions.getInputs(pathResolver)))),
//        pathResolver,
//        args.srcs.get(),
//        validateResources(
//            pathResolver,
//            params.getProjectFilesystem(),
//            args.resources.get()),
//        javacOptions.getGeneratedSourceFolderName(),
//        args.proguardConfig.transform(
//            SourcePaths.toSourcePath(params.getProjectFilesystem())),
//        args.postprocessClassesCommands.get(),
//        exportedDeps,
//        resolver.getAllRules(args.providedDeps.get()),
//        new BuildTargetSourcePath(abiJarTarget),
//        javacOptions.trackClassUsage(),
//                /* additionalClasspathEntries */ ImmutableSet.<Path>of(),
//        new JavacToJarStepFactory(javacOptions, JavacOptionsAmender.IDENTITY),
//        args.resourcesRoot,
//        args.mavenCoords,
//        args.tests.get(),
//        javacOptions.getClassesToRemoveFromJar());
//
//
//    DefaultJavaLibrary defaultJavaLibrary = newJavaLibrary;
//
//    try {
//      defaultJavaLibrary = resolver.addToIndex(newJavaLibrary);
//    } catch (IllegalStateException ise) {
//    }
//
//    try {
//      resolver.addToIndex(
//          CalculateAbi.of(
//              abiJarTarget,
//              pathResolver,
//              params,
//              new BuildTargetSourcePath(defaultJavaLibrary.getBuildTarget())));
//    } catch (IllegalStateException ise) {
//    }
//
//    try {
//      addGwtModule(
//          resolver,
//          pathResolver,
//          params,
//          args);
//    } catch (IllegalStateException ise) {
//    }
//
//    if (!flavors.contains(JavaLibrary.MAVEN_JAR)) {
//      return defaultJavaLibrary;
//    } else {
//      return MavenUberJar.create(
//          defaultJavaLibrary,
//          Preconditions.checkNotNull(paramsWithMavenFlavor),
//          pathResolver,
//          args.mavenCoords,
//          args.mavenPomTemplate);
//    }
  }

  private <A extends Arg> BuildRule createJavaLibrary(
      BuildRuleParams params,
      BuildRuleResolver resolver,
      SourcePathResolver pathResolver,
      A args) {
    BuildTarget abiJarTarget = params.getBuildTarget().withAppendedFlavors(CalculateAbi.FLAVOR);
    resolver.addToIndex(
        CalculateAbi.of(
            abiJarTarget,
            pathResolver,
            params,
            new BuildTargetSourcePath(params.getBuildTarget())));

    JavacOptions javacOptions = JavacOptionsFactory.create(
        defaultOptions,
        params,
        resolver,
        pathResolver,
        args);

    ImmutableSortedSet<BuildRule> exportedDeps = resolver.getAllRules(args.exportedDeps.get());

    return new DefaultJavaLibrary(
        params.appendExtraDeps(
            Iterables.concat(
                BuildRules.getExportedRules(
                    Iterables.concat(
                        params.getDeclaredDeps().get(),
                        exportedDeps,
                        resolver.getAllRules(args.providedDeps.get()))),
                pathResolver.filterBuildRuleInputs(
                    javacOptions.getInputs(pathResolver)))),
        pathResolver,
        args.srcs.get(),
        validateResources(
            pathResolver,
            params.getProjectFilesystem(),
            args.resources.get()),
        javacOptions.getGeneratedSourceFolderName(),
        args.proguardConfig.transform(
            SourcePaths.toSourcePath(params.getProjectFilesystem())),
        args.postprocessClassesCommands.get(),
        exportedDeps,
        resolver.getAllRules(args.providedDeps.get()),
        new BuildTargetSourcePath(abiJarTarget),
        javacOptions.trackClassUsage(),
                /* additionalClasspathEntries */ ImmutableSet.<Path>of(),
        new JavacToJarStepFactory(javacOptions, JavacOptionsAmender.IDENTITY),
        args.resourcesRoot,
        args.mavenCoords,
        args.tests.get(),
        javacOptions.getClassesToRemoveFromJar());
  }

  @SuppressFieldNotInitialized
  public static class Arg extends JvmLibraryArg implements HasTests {
    public Optional<ImmutableSortedSet<SourcePath>> srcs;
    public Optional<ImmutableSortedSet<SourcePath>> resources;

    public Optional<Path> proguardConfig;
    public Optional<ImmutableList<String>> postprocessClassesCommands;

    @Hint(isInput = false)
    public Optional<Path> resourcesRoot;
    public Optional<String> mavenCoords;
    public Optional<SourcePath> mavenPomTemplate;

    public Optional<Boolean> autodeps;
    public Optional<ImmutableSortedSet<String>> generatedSymbols;
    public Optional<ImmutableSortedSet<BuildTarget>> providedDeps;
    public Optional<ImmutableSortedSet<BuildTarget>> exportedDeps;
    public Optional<ImmutableSortedSet<BuildTarget>> deps;

    @Hint(isDep = false)
    public Optional<ImmutableSortedSet<BuildTarget>> tests;

    @Override
    public ImmutableSortedSet<BuildTarget> getTests() {
      return tests.get();
    }
  }
}
