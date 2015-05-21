/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.android;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.ImmutableFlavor;
import com.facebook.buck.model.UnflavoredBuildTarget;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRuleType;
import com.facebook.buck.rules.BuildRules;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.infer.annotation.SuppressFieldNotInitialized;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import java.nio.file.Path;
import java.util.Collection;

/**
 * Description for a {@link BuildRule} that generates an {@code .aar} file.
 * <p>
 * This represents an Android Library Project packaged as an {@code .aar} bundle as specified by:
 * <a> http://tools.android.com/tech-docs/new-build-system/aar-format </>.
 * <p>
 * Note that the {@code aar} may be specified as a {@link SourcePath}, so it could be either
 * a binary {@code .aar} file checked into version control, or a zip file that conforms to the
 * {@code .aar} specification that is generated by another build rule.
 */
public class AndroidAarDescription implements Description<AndroidAarDescription.Arg> {

  public static final BuildRuleType TYPE = BuildRuleType.of("android_aar");

  private static final Flavor AAR_ANDROID_MANIFEST_FLAVOR =
      ImmutableFlavor.of("aar_android_manifest");
  private static final Flavor AAR_ASSEMBLE_RESOURCE_FLAVOR =
      ImmutableFlavor.of("aar_assemble_resource");
  private static final Flavor AAR_ASSEMBLE_ASSETS_FLAVOR =
      ImmutableFlavor.of("aar_assemble_assets");
  private static final Flavor AAR_ANDROID_RESOURCE_FLAVOR =
      ImmutableFlavor.of("aar_android_resource");

  private final AndroidManifestDescription androidManifestDescription;

  public AndroidAarDescription(AndroidManifestDescription androidManifestDescription) {
    this.androidManifestDescription = androidManifestDescription;
  }

  @Override
  public BuildRuleType getBuildRuleType() {
    return TYPE;
  }

  @Override
  public Arg createUnpopulatedConstructorArg() {
    return new Arg();
  }

  @Override
  public <A extends Arg> BuildRule createBuildRule(
      BuildRuleParams originalBuildRuleParams,
      BuildRuleResolver resolver,
      A args) {

    UnflavoredBuildTarget originalBuildTarget =
        originalBuildRuleParams.getBuildTarget().checkUnflavored();
    SourcePathResolver pathResolver = new SourcePathResolver(resolver);
    ImmutableList.Builder<BuildRule> depRules = ImmutableList.builder();

    /* android_manifest */
    AndroidManifestDescription.Arg androidManifestArgs =
        androidManifestDescription.createUnpopulatedConstructorArg();
    androidManifestArgs.skeleton = args.manifestSkeleton;
    androidManifestArgs.deps = args.deps;

    BuildRuleParams androidManifestParams = originalBuildRuleParams.copyWithChanges(
        AndroidManifestDescription.TYPE,
        BuildTargets.createFlavoredBuildTarget(originalBuildTarget, AAR_ANDROID_MANIFEST_FLAVOR),
        Suppliers.ofInstance(originalBuildRuleParams.getDeclaredDeps()),
        Suppliers.ofInstance(originalBuildRuleParams.getExtraDeps()));

    AndroidManifest manifest = androidManifestDescription.createBuildRule(
        androidManifestParams,
        resolver,
        androidManifestArgs);
    depRules.add(resolver.addToIndex(manifest));

    /* assemble dirs */
    AndroidPackageableCollector collector =
        new AndroidPackageableCollector(
            originalBuildRuleParams.getBuildTarget(),
            ImmutableSet.<BuildTarget>of(),
            ImmutableSet.<BuildTarget>of());
    collector.addPackageables(AndroidPackageableCollector.getPackageableRules(
            originalBuildRuleParams.getDeps()));
    AndroidPackageableCollection packageableCollection = collector.build();

    ImmutableSortedSet<BuildRule> androidResourceDeclaredDeps =
        AndroidResourceHelper.androidResOnly(originalBuildRuleParams.getDeclaredDeps());
    ImmutableSortedSet<BuildRule> androidResourceExtraDeps =
        AndroidResourceHelper.androidResOnly(originalBuildRuleParams.getExtraDeps());

    BuildRuleParams assembleAssetsParams = originalBuildRuleParams.copyWithChanges(
        originalBuildRuleParams.getBuildRuleType(),
        BuildTargets.createFlavoredBuildTarget(originalBuildTarget, AAR_ASSEMBLE_ASSETS_FLAVOR),
        Suppliers.ofInstance(androidResourceDeclaredDeps),
        Suppliers.ofInstance(androidResourceExtraDeps));
    ImmutableCollection<SourcePath> assetsDirectories =
        packageableCollection.getAssetsDirectories();
    AssembleDirectories assembleAssetsDirectories = new AssembleDirectories(
        assembleAssetsParams,
        pathResolver,
        assetsDirectories);
    depRules.add(resolver.addToIndex(assembleAssetsDirectories));

    BuildRuleParams assembleResourceParams = originalBuildRuleParams.copyWithChanges(
        originalBuildRuleParams.getBuildRuleType(),
        BuildTargets.createFlavoredBuildTarget(originalBuildTarget, AAR_ASSEMBLE_RESOURCE_FLAVOR),
        Suppliers.ofInstance(androidResourceDeclaredDeps),
        Suppliers.ofInstance(androidResourceExtraDeps));
    ImmutableCollection<SourcePath> resDirectories =
        packageableCollection.getResourceDetails().getResourceDirectories();
    AssembleDirectories assembleResourceDirectories = new AssembleDirectories(
        assembleResourceParams,
        pathResolver,
        resDirectories);
    depRules.add(resolver.addToIndex(assembleResourceDirectories));

    /* android_resource */
    BuildRuleParams androidResourceParams = originalBuildRuleParams.copyWithChanges(
        AndroidLibraryDescription.TYPE,
        BuildTargets.createFlavoredBuildTarget(originalBuildTarget, AAR_ANDROID_RESOURCE_FLAVOR),
        Suppliers.ofInstance(ImmutableSortedSet.<BuildRule>of(
                manifest,
                assembleAssetsDirectories,
                assembleResourceDirectories)),
        Suppliers.ofInstance(ImmutableSortedSet.<BuildRule>of()));

    AndroidResource androidResource = new AndroidResource(
        androidResourceParams,
        pathResolver,
        /* deps */ ImmutableSortedSet.copyOf(depRules.build()),
        new BuildTargetSourcePath(
            assembleResourceDirectories.getProjectFilesystem(),
            assembleResourceDirectories.getBuildTarget()),
        /* resSrcs */ ImmutableSortedSet.<Path>of(),
        /* rDotJavaPackage */ null,
        new BuildTargetSourcePath(
            assembleAssetsDirectories.getProjectFilesystem(),
            assembleAssetsDirectories.getBuildTarget()),
        /* assetsSrcs */ ImmutableSortedSet.<Path>of(),
        new BuildTargetSourcePath(manifest.getProjectFilesystem(), manifest.getBuildTarget()),
        /* hasWhitelistedStrings */ false);
    depRules.add(resolver.addToIndex(androidResource));

    /* android_aar */
    depRules.addAll(
        getTargetsAsRules(
            packageableCollection.getNativeLibsTargets(),
            BuildTarget.of(originalBuildTarget),
            resolver));

    BuildRuleParams androidAarParams = originalBuildRuleParams.copyWithChanges(
        TYPE,
        BuildTarget.of(originalBuildTarget),
        Suppliers.ofInstance(ImmutableSortedSet.copyOf(depRules.build())),
        Suppliers.ofInstance(ImmutableSortedSet.<BuildRule>of()));

    return new AndroidAar(
        androidAarParams,
        pathResolver,
        manifest,
        androidResource,
        assembleResourceDirectories,
        assembleAssetsDirectories,
        packageableCollection.getNativeLibAssetsDirectories(),
        packageableCollection.getNativeLibsDirectories());
  }

  private ImmutableSortedSet<BuildRule> getTargetsAsRules(
      Collection<BuildTarget> buildTargets,
      BuildTarget originalBuildTarget,
      BuildRuleResolver ruleResolver) {
    return BuildRules.toBuildRulesFor(
        originalBuildTarget,
        ruleResolver,
        buildTargets);
  }

  @SuppressFieldNotInitialized
  public static class Arg extends AndroidLibraryDescription.Arg {
    public SourcePath manifestSkeleton;
  }
}