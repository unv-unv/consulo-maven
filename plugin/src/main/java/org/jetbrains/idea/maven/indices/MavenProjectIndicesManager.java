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
package org.jetbrains.idea.maven.indices;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.maven.rt.server.common.model.MavenArtifactInfo;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.maven.rt.server.common.model.MavenRemoteRepository;
import consulo.maven.rt.server.common.server.NativeMavenProjectHolder;
import consulo.project.Project;
import consulo.ui.ex.awt.util.MergingUpdateQueue;
import consulo.ui.ex.awt.util.Update;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.lucene.search.Query;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.utils.MavenMergingUpdateQueue;
import org.jetbrains.idea.maven.utils.MavenSimpleProjectComponent;

import jakarta.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@Singleton
@ServiceAPI(value = ComponentScope.PROJECT, lazy = false)
@ServiceImpl
public class MavenProjectIndicesManager extends MavenSimpleProjectComponent {
    private volatile List<MavenIndex> myProjectIndices = new ArrayList<>();
    private final MergingUpdateQueue myUpdateQueue;

    public static MavenProjectIndicesManager getInstance(Project p) {
        return p.getComponent(MavenProjectIndicesManager.class);
    }

    @Inject
    public MavenProjectIndicesManager(Project project) {
        super(project);
        myUpdateQueue = new MavenMergingUpdateQueue(getClass().getSimpleName(), 1000, true, project);

        if (!isNormalProject()) {
            return;
        }
        doInit();
    }

    public void doInit() {
        if (Application.get().isUnitTestMode()) {
            scheduleUpdateIndicesList();
        }

        getMavenProjectManager().addManagerListener(new MavenProjectsManager.Listener() {
            @Override
            public void activated() {
                scheduleUpdateIndicesList();
            }
        });

        getMavenProjectManager().addProjectsTreeListener(new MavenProjectsTree.Listener() {
            @Override
            public void projectsUpdated(List<Pair<MavenProject, MavenProjectChanges>> updated, List<MavenProject> deleted) {
                scheduleUpdateIndicesList();
            }

            @Override
            public void projectResolved(
                Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                NativeMavenProjectHolder nativeMavenProject
            ) {
                scheduleUpdateIndicesList();
            }
        });
    }

    private void scheduleUpdateIndicesList() {
        scheduleUpdateIndicesList(null);
    }

    public void scheduleUpdateIndicesList(@Nullable final Consumer<List<MavenIndex>> consumer) {
        myUpdateQueue.queue(new Update(MavenProjectIndicesManager.this) {
            @Override
            public void run() {
                if (myProject.isDisposed()) {
                    return;
                }

                Set<Pair<String, String>> remoteRepositoriesIdsAndUrls = ReadAction.compute(() -> collectRemoteRepositoriesIdsAndUrls());
                File localRepository = ReadAction.compute(() -> getLocalRepository());

                myProjectIndices =
                    MavenIndicesManager.getInstance().ensureIndicesExist(myProject, localRepository, remoteRepositoriesIdsAndUrls);
                if (consumer != null) {
                    consumer.accept(myProjectIndices);
                }
            }
        });
    }

    private File getLocalRepository() {
        return MavenProjectsManager.getInstance(myProject).getLocalRepository();
    }

    private Set<Pair<String, String>> collectRemoteRepositoriesIdsAndUrls() {
        Set<Pair<String, String>> result = new HashSet<>();
        Set<MavenRemoteRepository> remoteRepositories = new HashSet<>(getMavenProjectManager().getRemoteRepositories());
        for (MavenRepositoryProvider repositoryProvider : MavenRepositoryProvider.EP_NAME.getExtensionList()) {
            ContainerUtil.addAll(remoteRepositories, repositoryProvider.getRemoteRepositories(myProject));
        }
        for (MavenRemoteRepository each : remoteRepositories) {
            String id = each.getId();
            String url = each.getUrl();

            result.add(Couple.of(id, url));
        }
        return result;
    }

    public List<MavenIndex> getIndices() {
        return new ArrayList<>(myProjectIndices);
    }

    public void scheduleUpdateAll() {
        MavenIndicesManager.getInstance().scheduleUpdate(myProject, myProjectIndices);
    }

    public void scheduleUpdate(List<MavenIndex> indices) {
        MavenIndicesManager.getInstance().scheduleUpdate(myProject, indices);
    }

    public MavenIndicesManager.IndexUpdatingState getUpdatingState(MavenIndex index) {
        return MavenIndicesManager.getInstance().getUpdatingState(index);
    }

    private MavenProjectsManager getMavenProjectManager() {
        return MavenProjectsManager.getInstance(myProject);
    }

    public Set<String> getGroupIds() {
        Set<String> result = getProjectGroupIds();
        for (MavenIndex each : myProjectIndices) {
            result.addAll(each.getGroupIds());
        }
        return result;
    }

    public Set<String> getArtifactIds(String groupId) {
        Set<String> result = getProjectArtifactIds(groupId);
        for (MavenIndex each : myProjectIndices) {
            result.addAll(each.getArtifactIds(groupId));
        }
        return result;
    }

    public Set<String> getVersions(String groupId, String artifactId) {
        Set<String> result = getProjectVersions(groupId, artifactId);
        for (MavenIndex each : myProjectIndices) {
            result.addAll(each.getVersions(groupId, artifactId));
        }
        return result;
    }

    public boolean hasGroupId(String groupId) {
        if (hasProjectGroupId(groupId)) {
            return true;
        }
        for (MavenIndex each : myProjectIndices) {
            if (each.hasGroupId(groupId)) {
                return true;
            }
        }
        return checkLocalRepository(groupId, null, null);
    }

    private boolean checkLocalRepository(String groupId, String artifactId, String version) {
        if (StringUtil.isEmpty(groupId)) {
            return false;
        }

        String relPath = groupId.replace('.', '/');

        if (artifactId != null) {
            relPath += "/" + artifactId;
            if (version != null) {
                relPath += "/" + version + "/" + artifactId + "-" + version + ".pom";
            }
        }
        File file = new File(getLocalRepository(), relPath);

        return file.exists();
    }

    public boolean hasArtifactId(String groupId, String artifactId) {
        if (hasProjectArtifactId(groupId, artifactId)) {
            return true;
        }
        for (MavenIndex each : myProjectIndices) {
            if (each.hasArtifactId(groupId, artifactId)) {
                return true;
            }
        }
        return checkLocalRepository(groupId, artifactId, null);
    }

    public boolean hasVersion(String groupId, String artifactId, String version) {
        if (hasProjectVersion(groupId, artifactId, version)) {
            return true;
        }
        for (MavenIndex each : myProjectIndices) {
            if (each.hasVersion(groupId, artifactId, version)) {
                return true;
            }
        }
        return checkLocalRepository(groupId, artifactId, version);
    }

    public Set<MavenArtifactInfo> search(Query query, int maxResult) {
        Set<MavenArtifactInfo> result = new HashSet<>();

        for (MavenIndex each : myProjectIndices) {
            int remained = maxResult - result.size();
            if (remained <= 0) {
                break;
            }
            result.addAll(each.search(query, remained));
        }

        return result;
    }

    private Set<String> getProjectGroupIds() {
        Set<String> result = new HashSet<>();
        for (MavenId each : getProjectsIds()) {
            result.add(each.getGroupId());
        }
        return result;
    }

    private Set<String> getProjectArtifactIds(String groupId) {
        Set<String> result = new HashSet<>();
        for (MavenId each : getProjectsIds()) {
            if (groupId.equals(each.getGroupId())) {
                result.add(each.getArtifactId());
            }
        }
        return result;
    }

    private Set<String> getProjectVersions(String groupId, String artifactId) {
        Set<String> result = new HashSet<>();
        for (MavenId each : getProjectsIds()) {
            if (groupId.equals(each.getGroupId()) && artifactId.equals(each.getArtifactId())) {
                result.add(each.getVersion());
            }
        }
        return result;
    }

    private boolean hasProjectGroupId(String groupId) {
        return getProjectGroupIds().contains(groupId);
    }

    private boolean hasProjectArtifactId(String groupId, String artifactId) {
        return getProjectArtifactIds(groupId).contains(artifactId);
    }

    private boolean hasProjectVersion(String groupId, String artifactId, String version) {
        return getProjectVersions(groupId, artifactId).contains(version);
    }

    private Set<MavenId> getProjectsIds() {
        Set<MavenId> result = new HashSet<>();
        for (MavenProject each : MavenProjectsManager.getInstance(myProject).getProjects()) {
            result.add(each.getMavenId());
        }
        return result;
    }
}
