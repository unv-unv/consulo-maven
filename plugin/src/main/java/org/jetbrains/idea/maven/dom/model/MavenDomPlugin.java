/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import consulo.xml.util.xml.GenericDomValue;
import consulo.xml.util.xml.Required;
import org.jetbrains.idea.maven.dom.MavenDomElement;

import jakarta.annotation.Nonnull;

/**
 * http://maven.apache.org/POM/4.0.0:Plugin interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:Plugin documentation</h3>
 * 4.0.0
 * </pre>
 */
public interface MavenDomPlugin extends MavenDomElement, MavenDomArtifactCoordinates {
    @Required(value = false, nonEmpty = true)
    GenericDomValue<String> getGroupId();

    @Required(value = false, nonEmpty = true)
    GenericDomValue<String> getVersion();

    /**
     * Returns the value of the extensions child.
     * <pre>
     * <h3>Element http://maven.apache.org/POM/4.0.0:extensions documentation</h3>
     * 4.0.0
     * </pre>
     *
     * @return the value of the extensions child.
     */
    @Nonnull
    @Required(value = false, nonEmpty = true)
    GenericDomValue<Boolean> getExtensions();

    /**
     * Returns the value of the executions child.
     * <pre>
     * <h3>Element http://maven.apache.org/POM/4.0.0:executions documentation</h3>
     * 4.0.0
     * </pre>
     *
     * @return the value of the executions child.
     */
    @Nonnull
    MavenDomExecutions getExecutions();

    /**
     * Returns the value of the dependencies child.
     * <pre>
     * <h3>Element http://maven.apache.org/POM/4.0.0:dependencies documentation</h3>
     * 4.0.0
     * </pre>
     *
     * @return the value of the dependencies child.
     */
    @Nonnull
    MavenDomDependencies getDependencies();

    /**
     * Returns the value of the goals child.
     * <pre>
     * <h3>Element http://maven.apache.org/POM/4.0.0:goals documentation</h3>
     * 4.0.0
     * </pre>
     *
     * @return the value of the goals child.
     */
    @Nonnull
    MavenDomGoals getGoals();

    /**
     * Returns the value of the inherited child.
     * <pre>
     * <h3>Element http://maven.apache.org/POM/4.0.0:inherited documentation</h3>
     * 4.0.0
     * </pre>
     *
     * @return the value of the inherited child.
     */
    @Nonnull
    @Required(value = false, nonEmpty = true)
    GenericDomValue<Boolean> getInherited();

    /**
     * Returns the value of the configuration child.
     * <pre>
     * <h3>Element http://maven.apache.org/POM/4.0.0:configuration documentation</h3>
     * 0.0.0+
     * </pre>
     *
     * @return the value of the configuration child.
     */
    @Nonnull
    MavenDomConfiguration getConfiguration();
}
