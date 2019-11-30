/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import consulo.ui.annotation.RequiredUIAccess;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

import javax.annotation.Nonnull;

/**
 * @author Sergey Evdokimov
 */
public class RemoveMavenRunConfigurationAction extends AnAction
{
	@RequiredUIAccess
	@Override
	public void actionPerformed(@Nonnull AnActionEvent e)
	{
		Project project = e.getProject();
		RunnerAndConfigurationSettings settings = e.getData(MavenDataKeys.RUN_CONFIGURATION);

		assert settings != null && project != null;

		int res = Messages.showYesNoDialog(project, "Delete \"" + settings.getName() + "\"?", "Confirmation", Messages.getQuestionIcon());
		if(res == Messages.YES)
		{
			((RunManagerEx) RunManager.getInstance(project)).removeConfiguration(settings);
		}
	}

	@RequiredUIAccess
	@Override
	public void update(@Nonnull AnActionEvent e)
	{
		Project project = e.getProject();
		RunnerAndConfigurationSettings settings = e.getData(MavenDataKeys.RUN_CONFIGURATION);

		boolean enabled = settings != null && project != null;
		e.getPresentation().setEnabledAndVisible(enabled);
	}
}
