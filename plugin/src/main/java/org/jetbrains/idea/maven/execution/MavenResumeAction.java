/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import consulo.annotation.access.RequiredReadAction;
import consulo.application.AllIcons;
import consulo.application.Application;
import consulo.execution.RunCanceledByUserException;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.ExecutionEnvironmentBuilder;
import consulo.execution.runner.ProgramRunner;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.logging.Logger;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessOutputTypes;
import consulo.process.event.ProcessAdapter;
import consulo.process.event.ProcessEvent;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.Messages;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Sergey Evdokimov
 */
public class MavenResumeAction extends AnAction {

    private static final Logger LOG = Logger.getInstance(MavenResumeAction.class);

    private static final Set<String> PARAMS_DISABLING_RESUME = Set.of(
        "-rf",
        "-resume-from",
        "-pl",
        "-projects",
        "-am",
        "-also-make",
        "-amd",
        "-also-make-dependents"
    );

    public static final int STATE_INITIAL = 0;
    public static final int STATE_READING_PROJECT_LIST = 1;
    public static final int STATE_READING_PROJECT_LIST_OLD_MAVEN = 5;
    public static final int STATE_WAIT_FOR_BUILD = 2;
    public static final int STATE_WAIT_FOR______ = 3;
    public static final int STATE_WTF = -1;

    private final ProgramRunner myRunner;
    private final ExecutionEnvironment myEnvironment;

    private int myState = STATE_INITIAL;

    private int myBuildingProjectIndex = 0;

    private final List<String> myMavenProjectNames = new ArrayList<>();

    private String myResumeFromModuleName;

    private String myResumeModuleId;

    public MavenResumeAction(
        ProcessHandler processHandler,
        ProgramRunner runner,
        ExecutionEnvironment environment
    ) {
        super("Resume build from specified module", null, AllIcons.RunConfigurations.RerunFailedTests);
        myRunner = runner;
        myEnvironment = environment;

        final MavenRunConfiguration runConfiguration = (MavenRunConfiguration)environment.getRunProfile();

        getTemplatePresentation().setEnabled(false);

        processHandler.addProcessListener(new ProcessAdapter() {
            @Override
            public void processTerminated(ProcessEvent event) {
                if (myState == STATE_WTF) {
                    return;
                }

                if (event.getExitCode() == 0 && myBuildingProjectIndex != myMavenProjectNames.size()) {
                    log(String.format(
                        "Build was success, but not all project was build. Project build order: %s, build index: %d",
                        myMavenProjectNames,
                        myBuildingProjectIndex
                    ));
                }

                if (event.getExitCode() == 1 && myBuildingProjectIndex > 0) {
                    if (myBuildingProjectIndex == 1 && !hasResumeFromParameter(runConfiguration)) {
                        return;
                    }

                    myResumeFromModuleName = myMavenProjectNames.get(myBuildingProjectIndex - 1);

                    MavenProject mavenProject = findProjectByName(myResumeFromModuleName);
                    if (mavenProject != null) {
                        myResumeModuleId = mavenProject.getMavenId().getGroupId() + ':' + mavenProject.getMavenId().getArtifactId();
                    }
                }
            }

            @Override
            public void onTextAvailable(ProcessEvent event, Key outputType) {
                if (outputType != ProcessOutputTypes.STDOUT) {
                    return;
                }

                String text = event.getText().trim();
                if (text.isEmpty()) {
                    return;
                }

                String textWithoutInfo = "";
                if (text.startsWith("[INFO] ")) {
                    textWithoutInfo = text.substring("[INFO] ".length()).trim();
                }

                switch (myState) {
                    case STATE_INITIAL: // initial state.
                        if (textWithoutInfo.equals("Reactor build order:")) {
                            myState = STATE_READING_PROJECT_LIST_OLD_MAVEN;
                        }
                        else if (textWithoutInfo.equals("Reactor Build Order:")) {
                            myState = STATE_READING_PROJECT_LIST;
                        }
                        break;

                    case STATE_READING_PROJECT_LIST:
                        if (textWithoutInfo.equals("------------------------------------------------------------------------")) {
                            myState = STATE_WAIT_FOR_BUILD;
                        }
                        else if (textWithoutInfo.length() > 0) {
                            myMavenProjectNames.add(textWithoutInfo);
                        }
                        break;

                    case STATE_READING_PROJECT_LIST_OLD_MAVEN:
                        if (textWithoutInfo.length() > 0) {
                            if (text.startsWith("[INFO]   ")) {
                                myMavenProjectNames.add(textWithoutInfo);
                            }
                            else {
                                myState = STATE_WAIT_FOR_BUILD;
                            }
                        }
                        break;

                    case STATE_WAIT_FOR_BUILD:
                        if (textWithoutInfo.startsWith("Building ")) {
                            String projectName = textWithoutInfo.substring("Building ".length());
                            if (myBuildingProjectIndex >= myMavenProjectNames.size() ||
                                !projectName.startsWith(myMavenProjectNames.get(myBuildingProjectIndex))) {
                                myState = STATE_WTF;
                                log(String.format("Invalid project building order. Defined order: %s, error index: %d, invalid line: %s",
                                    myMavenProjectNames, myBuildingProjectIndex, text
                                ));
                                break;
                            }

                            myBuildingProjectIndex++;
                        }
                        myState = STATE_WAIT_FOR______;
                        break;

                    case STATE_WAIT_FOR______:
                        if (textWithoutInfo.equals("------------------------------------------------------------------------")) {
                            myState = STATE_WAIT_FOR_BUILD;
                        }
                        break;

                    case STATE_WTF:
                        break;

                    default:
                        throw new IllegalStateException();
                }
            }
        });
    }

    private static boolean hasResumeFromParameter(MavenRunConfiguration runConfiguration) {
        List<String> goals = runConfiguration.getRunnerParameters().getGoals();
        return goals.size() > 2 && "-rf".equals(goals.get(goals.size() - 2));
    }

    @Nullable
    private MavenProject findProjectByName(@Nonnull String projectName) {
        List<MavenProject> projects = MavenProjectsManager.getInstance(myEnvironment.getProject()).getProjects();

        MavenProject candidate = null;

        for (MavenProject mavenProject : projects) {
            if (projectName.equals(mavenProject.getName())) {
                if (candidate == null) {
                    candidate = mavenProject;
                }
                else {
                    return null;
                }
            }
        }

        if (candidate != null) {
            return candidate;
        }

        for (MavenProject mavenProject : projects) {
            String id = mavenProject.getMavenId().getGroupId() + ':' + mavenProject.getMavenId()
                .getArtifactId() + ':' + mavenProject.getPackaging();
            if (projectName.contains(id)) {
                if (candidate == null) {
                    candidate = mavenProject;
                }
                else {
                    return null;
                }
            }
        }

        if (candidate != null) {
            return candidate;
        }

        for (MavenProject mavenProject : projects) {
            if (projectName.equals(mavenProject.getMavenId().getArtifactId())) {
                if (candidate == null) {
                    candidate = mavenProject;
                }
                else {
                    return null;
                }
            }
        }

        return candidate;
    }

    @RequiredReadAction
    public static boolean isApplicable(
        @Nullable Project project,
        OwnJavaParameters javaParameters,
        MavenRunConfiguration runConfiguration
    ) {
        if (hasResumeFromParameter(runConfiguration)) { // This runConfiguration was created by other MavenResumeAction.
            MavenRunConfiguration clonedRunConf = runConfiguration.clone();
            List<String> clonedGoals = clonedRunConf.getRunnerParameters().getGoals();
            clonedGoals.remove(clonedGoals.size() - 1);
            clonedGoals.remove(clonedGoals.size() - 1);
            try {
                javaParameters = clonedRunConf.createJavaParameters(project);
            }
            catch (ExecutionException e) {
                return false;
            }
        }

        for (String params : javaParameters.getProgramParametersList().getList()) {
            if (PARAMS_DISABLING_RESUME.contains(params)) {
                return false;
            }
        }

        return true;
    }

    private static void log(String message) {
        if (Application.get().isInternal()) {
            LOG.error(message, new Exception());
        }
        else {
            LOG.warn(message, new Exception());
        }
    }

    @Override
    @RequiredUIAccess
    public void update(@Nonnull AnActionEvent e) {
        if (myResumeFromModuleName != null && myResumeModuleId != null) {
            e.getPresentation().setEnabled(true);
            e.getPresentation().setText("Resume build from \"" + myResumeFromModuleName + "\"");
        }
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = myEnvironment.getProject();
        try {
            MavenRunConfiguration runConfiguration = ((MavenRunConfiguration)myEnvironment.getRunProfile()).clone();

            List<String> goals = runConfiguration.getRunnerParameters().getGoals();

            if (goals.size() > 2 && "-rf".equals(goals.get(goals.size() - 2))) {
                // This runConfiguration was created by other MavenResumeAction.
                goals.set(goals.size() - 1, myResumeModuleId);
            }
            else {
                goals.add("-rf");
                goals.add(myResumeModuleId);
            }

            myRunner.execute(new ExecutionEnvironmentBuilder(myEnvironment).contentToReuse(null).build());
        }
        catch (RunCanceledByUserException ignore) {
        }
        catch (ExecutionException e1) {
            Messages.showErrorDialog(project, e1.getMessage(), ExecutionLocalize.restartErrorMessageTitle().get());
        }
    }
}
