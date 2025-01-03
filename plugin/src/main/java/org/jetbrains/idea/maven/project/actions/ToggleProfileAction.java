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
package org.jetbrains.idea.maven.project.actions;

import consulo.localize.LocalizeValue;
import consulo.maven.rt.server.common.model.MavenExplicitProfiles;
import consulo.maven.rt.server.common.model.MavenProfileKind;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import jakarta.annotation.Nullable;
import java.util.Map;
import java.util.Set;

public class ToggleProfileAction extends MavenAction {
    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        if (!isAvailable(e)) {
            return;
        }

        MavenProfileKind targetState = getTargetState(e);
        if (targetState == null) {
            return;
        }
        LocalizeValue text = switch (targetState) {
            case NONE -> MavenProjectLocalize.mavenProfileDeactivate();
            case EXPLICIT -> MavenProjectLocalize.mavenProfileActivate();
            default -> MavenProjectLocalize.mavenProfileDefault();
        };
        e.getPresentation().setTextValue(text);
    }

    @Override
    protected boolean isAvailable(AnActionEvent e) {
        return super.isAvailable(e) && getTargetState(e) != null;
    }

    @Nullable
    private static MavenProfileKind getTargetState(AnActionEvent e) {
        Map<String, MavenProfileKind> selectedProfiles = e.getData(MavenDataKeys.MAVEN_PROFILES);
        if (selectedProfiles == null || selectedProfiles.isEmpty()) {
            return null;
        }

        MavenProjectsManager projectsManager = MavenActionUtil.getProjectsManager(e.getDataContext());
        if (projectsManager == null) {
            return null;
        }
        return getTargetState(projectsManager, selectedProfiles);
    }

    @Nullable
    private static MavenProfileKind getTargetState(@Nonnull MavenProjectsManager projectsManager, Map<String, MavenProfileKind> profiles) {
        MavenExplicitProfiles explicitProfiles = projectsManager.getExplicitProfiles();
        MavenProfileKind targetState = null;
        // all profiles should target to the same state
        for (Map.Entry<String, MavenProfileKind> profile : profiles.entrySet()) {
            MavenProfileKind profileTargetState = getTargetState(profile, explicitProfiles);
            if (targetState == null) {
                targetState = profileTargetState;
            }
            else if (!targetState.equals(profileTargetState)) {
                targetState = null;
                break;
            }
        }
        return targetState;
    }

    private static MavenProfileKind getTargetState(Map.Entry<String, MavenProfileKind> profile, MavenExplicitProfiles explicitProfiles) {
        MavenProfileKind targetState;
        if (explicitProfiles.getDisabledProfiles().contains(profile.getKey())) {
            // explicitly disabled -> explicitly enabled
            targetState = MavenProfileKind.EXPLICIT;
        }
        else if (explicitProfiles.getEnabledProfiles().contains(profile.getKey())) {
            // explicitly enabled -> default
            targetState = MavenProfileKind.IMPLICIT;
        }
        else if (MavenProfileKind.NONE.equals(profile.getValue())) { // default
            // default inactive -> explicitly enabled
            targetState = MavenProfileKind.EXPLICIT;
        }
        else {
            // default active -> explicitly disabled
            targetState = MavenProfileKind.NONE;
        }
        return targetState;
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(@Nonnull AnActionEvent e) {
        MavenProjectsManager manager = MavenActionUtil.getProjectsManager(e.getDataContext());
        if (manager == null) {
            return;
        }
        Map<String, MavenProfileKind> selectedProfiles = e.getData(MavenDataKeys.MAVEN_PROFILES);
        if (selectedProfiles == null) {
            return;
        }

        Set<String> selectedProfileIds = selectedProfiles.keySet();

        MavenProfileKind targetState = getTargetState(manager, selectedProfiles);
        if (targetState == null) {
            return;
        }

        MavenExplicitProfiles newExplicitProfiles = manager.getExplicitProfiles().clone();
        switch (targetState) {
            case NONE:
                // disable explicitly
                newExplicitProfiles.getEnabledProfiles().removeAll(selectedProfileIds);
                newExplicitProfiles.getDisabledProfiles().addAll(selectedProfileIds);
                break;
            case EXPLICIT:
                // enable explicitly
                newExplicitProfiles.getDisabledProfiles().removeAll(selectedProfileIds);
                newExplicitProfiles.getEnabledProfiles().addAll(selectedProfileIds);
                break;
            case IMPLICIT:
            default:
                // reset to default state
                newExplicitProfiles.getEnabledProfiles().removeAll(selectedProfileIds);
                newExplicitProfiles.getDisabledProfiles().removeAll(selectedProfileIds);
                break;
        }
        manager.setExplicitProfiles(newExplicitProfiles);
    }
}
