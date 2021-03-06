/*
 * Copyright 2013-present Facebook, Inc.
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

package org.openqa.selenium.buck.javascript;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractDescriptionArg;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRuleType;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.TargetGraph;
import com.google.common.collect.ImmutableSortedSet;

import java.util.Optional;

public class JsLibraryDescription implements Description<JsLibraryDescription.Arg> {

  private static final BuildRuleType TYPE = BuildRuleType.of("js_library");

  @Override
  public BuildRuleType getBuildRuleType() {
    return TYPE;
  }

  @Override
  public Arg createUnpopulatedConstructorArg() {
    return new Arg();
  }

  @Override
  public <A extends Arg> JsLibrary createBuildRule(
      TargetGraph targetGraph,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      A args) {
    return new JsLibrary(
        params,
        new SourcePathResolver(resolver),
        params.getDeclaredDeps().get(),
        args.srcs);
  }

  public static class Arg extends AbstractDescriptionArg {
    public ImmutableSortedSet<SourcePath> srcs;
    public Optional<ImmutableSortedSet<BuildTarget>> deps;
  }
}
