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

package com.facebook.buck.jvm.java;

import com.facebook.buck.rules.BuildRule;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

public interface HasMavenCoordinates extends BuildRule {

  /**
   * Used to identify this library within a maven repository
   */
  Optional<String> getMavenCoords();

  Predicate<BuildRule> MAVEN_COORDS_PRESENT_PREDICATE =
      input -> input instanceof HasMavenCoordinates &&
          ((HasMavenCoordinates) input).getMavenCoords().isPresent();

  Function<BuildRule, String> NORMALIZE_COORDINATE =
      new Function<BuildRule, String>() {
        @Override
        @Nullable
        public String apply(BuildRule deriveFrom) {
          if (!(deriveFrom instanceof HasMavenCoordinates)) {
            return null;
          }

          HasMavenCoordinates mavenCoords = (HasMavenCoordinates) deriveFrom;
          if (!mavenCoords.getMavenCoords().isPresent()) {
            return null;
          }

          String coords = mavenCoords.getMavenCoords().get();
          Pattern p = Pattern.compile("([^: ]+):([^: ]+)(:([^: ]*)(:([^: ]+))?)?:([^: ]+)");
          Matcher m = p.matcher(coords);

          Preconditions.checkState(m.matches(), "Unable to parse maven coordinates: %s", coords);

          return
              m.group(1) + ':' + // group id
              m.group(2) + ':' + // artifact id
              m.group(7);        // version
        }
      };

  static boolean isMavenCoordsPresent(HasMavenCoordinates input) {
    return input.getMavenCoords().isPresent();
  }
}
