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
package org.jetbrains.idea.maven.server;

import consulo.virtualFileSystem.VirtualFile;
import consulo.maven.rt.server.common.model.*;
import consulo.maven.rt.server.common.server.*;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class MavenEmbedderWrapper extends RemoteObjectWrapper<MavenServerEmbedder> {
    private Customization myCustomization;

    public MavenEmbedderWrapper(@Nullable RemoteObjectWrapper<?> parent) {
        super(parent);
    }

    @Override
    protected synchronized void onWrappeeCreated() throws RemoteException {
        super.onWrappeeCreated();
        if (myCustomization != null) {
            doCustomize();
        }
    }

    public void customizeForResolve(MavenConsole console, MavenProgressIndicator indicator) {
        setCustomization(console, indicator, null, false, false);
        perform((Retriable<Object>)() -> {
            doCustomize();
            return null;
        });
    }

    public void customizeForResolve(
        MavenWorkspaceMap workspaceMap,
        MavenConsole console,
        MavenProgressIndicator indicator,
        boolean alwaysUpdateSnapshot
    ) {
        setCustomization(console, indicator, workspaceMap, false, alwaysUpdateSnapshot);
        perform((Retriable<Object>)() -> {
            doCustomize();
            return null;
        });
    }

    public void customizeForStrictResolve(MavenWorkspaceMap workspaceMap, MavenConsole console, MavenProgressIndicator indicator) {
        setCustomization(console, indicator, workspaceMap, true, false);
        perform((Retriable<Object>)() -> {
            doCustomize();
            return null;
        });
    }

    public void customizeForGetVersions() {
        perform((Retriable<Object>)() -> {
            doCustomizeComponents();
            return null;
        });
    }

    private synchronized void doCustomizeComponents() throws RemoteException {
        getOrCreateWrappee().customizeComponents();
    }

    private synchronized void doCustomize() throws RemoteException {
        getOrCreateWrappee().customize(
            myCustomization.workspaceMap,
            myCustomization.failOnUnresolvedDependency,
            myCustomization.console,
            myCustomization.indicator,
            myCustomization.alwaysUpdateSnapshot
        );
    }

    @Nonnull
    public MavenServerExecutionResult resolveProject(
        @Nonnull final VirtualFile file,
        @Nonnull final Collection<String> activeProfiles,
        @Nonnull final Collection<String> inactiveProfiles
    ) throws MavenProcessCanceledException {
        return perform((RetriableCancelable<MavenServerExecutionResult>)() -> getOrCreateWrappee().resolveProject(
            new File(file.getPath()),
            activeProfiles,
            inactiveProfiles
        ));
    }

    @Nullable
    public String evaluateEffectivePom(
        @Nonnull final VirtualFile file,
        @Nonnull final Collection<String> activeProfiles,
        @Nonnull final Collection<String> inactiveProfiles
    ) throws MavenProcessCanceledException {
        return perform((RetriableCancelable<String>)() -> getOrCreateWrappee().evaluateEffectivePom(
            new File(file.getPath()),
            new ArrayList<>(activeProfiles),
            new ArrayList<>(inactiveProfiles)
        ));
    }

    @Nonnull
    public MavenArtifact resolve(
        @Nonnull final MavenArtifactInfo info,
        @Nonnull final List<MavenRemoteRepository> remoteRepositories
    ) throws MavenProcessCanceledException {
        return perform((RetriableCancelable<MavenArtifact>)() -> getOrCreateWrappee().resolve(info, remoteRepositories));
    }

    @Nonnull
    public List<MavenArtifact> resolveTransitively(
        @Nonnull final List<MavenArtifactInfo> artifacts,
        @Nonnull final List<MavenRemoteRepository> remoteRepositories
    ) throws MavenProcessCanceledException {
        return perform((RetriableCancelable<List<MavenArtifact>>)() -> getOrCreateWrappee().resolveTransitively(
            artifacts,
            remoteRepositories
        ));
    }

    @Nonnull
    public List<String> retrieveVersions(
        @Nonnull final String groupId,
        @Nonnull final String artifactId,
        @Nonnull final List<MavenRemoteRepository> remoteRepositories
    ) throws MavenProcessCanceledException {
        return perform((RetriableCancelable<List<String>>)() -> getOrCreateWrappee().retrieveAvailableVersions(
            groupId,
            artifactId,
            remoteRepositories
        ));
    }

    public Collection<MavenArtifact> resolvePlugin(
        @Nonnull final MavenPlugin plugin,
        @Nonnull final List<MavenRemoteRepository> repositories,
        @Nonnull final NativeMavenProjectHolder nativeMavenProject,
        final boolean transitive
    ) throws MavenProcessCanceledException {
        int id;
        try {
            id = nativeMavenProject.getId();
        }
        catch (RemoteException e) {
            // do not call handleRemoteError here since this error occurred because of previous remote error
            return Collections.emptyList();
        }

        try {
            return getOrCreateWrappee().resolvePlugin(plugin, repositories, id, transitive);
        }
        catch (RemoteException e) {
            // do not try to reconnect here since we have lost NativeMavenProjectHolder anyway.
            handleRemoteError(e);
            return Collections.emptyList();
        }
        catch (MavenServerProcessCanceledException e) {
            throw new MavenProcessCanceledException();
        }
    }

    @Nonnull
    public MavenServerExecutionResult execute(
        @Nonnull final VirtualFile file,
        @Nonnull final Collection<String> activeProfiles,
        @Nonnull final Collection<String> inactiveProfiles,
        @Nonnull final List<String> goals
    ) throws MavenProcessCanceledException {
        return perform((RetriableCancelable<MavenServerExecutionResult>)() -> getOrCreateWrappee().execute(
            new File(file.getPath()),
            activeProfiles,
            inactiveProfiles,
            goals,
            Collections.<String>emptyList(),
            false,
            false
        ));
    }

    @Nonnull
    public MavenServerExecutionResult execute(
        @Nonnull final VirtualFile file,
        @Nonnull final Collection<String> activeProfiles,
        @Nonnull final Collection<String> inactiveProfiles,
        @Nonnull final List<String> goals,
        @Nonnull final List<String> selectedProjects,
        final boolean alsoMake,
        final boolean alsoMakeDependents
    ) throws MavenProcessCanceledException {
        return perform((RetriableCancelable<MavenServerExecutionResult>)() -> getOrCreateWrappee().execute(
            new File(file.getPath()),
            activeProfiles,
            inactiveProfiles,
            goals,
            selectedProjects,
            alsoMake,
            alsoMakeDependents
        ));
    }

    public void reset() {
        MavenServerEmbedder w = getWrappee();
        if (w == null) {
            return;
        }
        try {
            w.reset();
        }
        catch (RemoteException e) {
            handleRemoteError(e);
        }
        resetCustomization();
    }

    public void release() {
        MavenServerEmbedder w = getWrappee();
        if (w == null) {
            return;
        }
        try {
            w.release();
        }
        catch (RemoteException e) {
            handleRemoteError(e);
        }
        resetCustomization();
    }


    public void clearCaches() {
        MavenServerEmbedder w = getWrappee();
        if (w == null) {
            return;
        }
        try {
            w.clearCaches();
        }
        catch (RemoteException e) {
            handleRemoteError(e);
        }
    }

    public void clearCachesFor(MavenId projectId) {
        MavenServerEmbedder w = getWrappee();
        if (w == null) {
            return;
        }
        try {
            w.clearCachesFor(projectId);
        }
        catch (RemoteException e) {
            handleRemoteError(e);
        }
    }

    private synchronized void setCustomization(
        MavenConsole console, MavenProgressIndicator indicator,
        MavenWorkspaceMap workspaceMap,
        boolean failOnUnresolvedDependency,
        boolean alwaysUpdateSnapshot
    ) {
        resetCustomization();
        myCustomization = new Customization(
            MavenServerManager.wrapAndExport(console),
            MavenServerManager.wrapAndExport(indicator),
            workspaceMap,
            failOnUnresolvedDependency,
            alwaysUpdateSnapshot
        );
    }

    private synchronized void resetCustomization() {
        if (myCustomization == null) {
            return;
        }

        try {
            UnicastRemoteObject.unexportObject(myCustomization.console, true);
        }
        catch (NoSuchObjectException e) {
            MavenLog.LOG.warn(e);
        }
        try {
            UnicastRemoteObject.unexportObject(myCustomization.indicator, true);
        }
        catch (NoSuchObjectException e) {
            MavenLog.LOG.warn(e);
        }

        myCustomization = null;
    }

    private static class Customization {
        private final MavenServerConsole console;
        private final MavenServerProgressIndicator indicator;

        private final MavenWorkspaceMap workspaceMap;
        private final boolean failOnUnresolvedDependency;
        private final boolean alwaysUpdateSnapshot;

        private Customization(
            MavenServerConsole console,
            MavenServerProgressIndicator indicator,
            MavenWorkspaceMap workspaceMap,
            boolean failOnUnresolvedDependency,
            boolean alwaysUpdateSnapshot
        ) {
            this.console = console;
            this.indicator = indicator;
            this.workspaceMap = workspaceMap;
            this.failOnUnresolvedDependency = failOnUnresolvedDependency;
            this.alwaysUpdateSnapshot = alwaysUpdateSnapshot;
        }
    }
}
