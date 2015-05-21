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

package com.facebook.buck.rules;

import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.step.fs.SymlinkFileStep;
import com.google.common.collect.ImmutableList;

import java.nio.file.Path;

/**
 * {@link BuildRule} that wraps a file generated by another rule so that there can be a
 * {@link BuildTargetSourcePath} that corresponds to that file. This is frequently used with
 * rules/flavors that are generated via graph enhancement.
 */
public class OutputOnlyBuildRule extends AbstractBuildRule {

  // We can stringify, since this is the output of another rule.
  @AddToRuleKey(stringify = true)
  private final Path input;
  private final Path output;

  public OutputOnlyBuildRule(
      BuildRuleParams buildRuleParams,
      SourcePathResolver resolver,
      Path existingOutput) {
    super(buildRuleParams, resolver);

    this.input = existingOutput;
    this.output = BuildTargets.getGenPath(buildRuleParams.getBuildTarget(), "%s")
        .resolve(existingOutput.getFileName());
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context,
      BuildableContext buildableContext) {
    ImmutableList.Builder<Step> steps = ImmutableList.builder();

    steps.add(new MakeCleanDirectoryStep(output.getParent()));
    steps.add(new SymlinkFileStep(input, output, false));

    buildableContext.recordArtifact(output);

    return ImmutableList.of();
  }

  @Override
  public Path getPathToOutputFile() {
    return output;
  }

}