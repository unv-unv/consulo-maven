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

package org.jetbrains.idea.maven.execution;

import consulo.annotation.component.ExtensionImpl;
import consulo.module.Module;
import consulo.module.content.layer.OrderEnumerationPolicy;
import consulo.project.Project;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class MavenOrderEnumeratorHandler implements OrderEnumerationPolicy {
    @Override
    public boolean isApplicable(@Nonnull Project project) {
        return MavenProjectsManager.getInstance(project).isMavenizedProject();
    }

    @Override
    public boolean isApplicable(@Nonnull Module module) {
        final MavenProjectsManager manager = MavenProjectsManager.getInstance(module.getProject());
        return manager.isMavenizedModule(module);
    }

    @Override
    public boolean shouldAddRuntimeDependenciesToTestCompilationClasspath() {
        return true;
    }

    @Override
    public boolean shouldIncludeTestsFromDependentModulesToTestClasspath() {
        return false;
    }

    @Override
    public boolean shouldProcessDependenciesRecursively() {
        return false;
    }
}
