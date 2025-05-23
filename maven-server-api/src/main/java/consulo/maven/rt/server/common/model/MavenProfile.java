/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.maven.rt.server.common.model;

import jakarta.annotation.Nonnull;

import java.io.Serializable;

public class MavenProfile extends MavenModelBase implements Serializable {
  private final String myId;
  private final String mySource;
  private MavenProfileActivation myActivation;
  private final MavenBuildBase myBuild = new MavenBuildBase();

  public MavenProfile(String id, String source) {
    myId = id;
    mySource = source;
  }

  @Nonnull
  public String getId() {
    return myId;
  }

  public String getSource() {
    return mySource;
  }

  public void setActivation(MavenProfileActivation activation) {
    myActivation = activation;
  }

  public MavenProfileActivation getActivation() {
    return myActivation;
  }

  public MavenBuildBase getBuild() {
    return myBuild;
  }
}
