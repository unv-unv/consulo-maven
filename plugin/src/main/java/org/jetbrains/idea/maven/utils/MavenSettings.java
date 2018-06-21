/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.swing.JComponent;

import org.jetbrains.annotations.Nls;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerConfigurable;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.indices.MavenRepositoriesConfigurable;
import org.jetbrains.idea.maven.project.MavenGeneralConfigurable;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenIgnoredFilesConfigurable;
import org.jetbrains.idea.maven.project.MavenImportingConfigurable;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import consulo.annotations.RequiredDispatchThread;

public class MavenSettings implements SearchableConfigurable.Parent
{
	public static final String DISPLAY_NAME = "Maven";

	private final Project myProject;
	private final Configurable myConfigurable;
	private final List<Configurable> myChildren;

	public MavenSettings(@Nonnull Project project)
	{
		myProject = project;

		myConfigurable = new MavenGeneralConfigurable()
		{
			@Override
			protected MavenGeneralSettings getState()
			{
				return MavenProjectsManager.getInstance(myProject).getGeneralSettings();
			}
		};

		myChildren = new ArrayList<>();
		myChildren.add(new MavenImportingConfigurable(myProject));
		myChildren.add(new MavenIgnoredFilesConfigurable(myProject));

		myChildren.add(new MyMavenRunnerConfigurable(project));

		//myChildren.add(new MavenTestRunningConfigurable(project));

		if(!myProject.isDefault())
		{
			myChildren.add(new MavenRepositoriesConfigurable(myProject));
		}
	}

	@Override
	public boolean hasOwnContent()
	{
		return true;
	}

	@Override
	public boolean isVisible()
	{
		return true;
	}

	@RequiredDispatchThread
	@Override
	public JComponent createComponent()
	{
		return myConfigurable.createComponent();
	}

	@RequiredDispatchThread
	@Override
	public boolean isModified()
	{
		return myConfigurable.isModified();
	}

	@RequiredDispatchThread
	@Override
	public void apply() throws ConfigurationException
	{
		myConfigurable.apply();
	}

	@RequiredDispatchThread
	@Override
	public void reset()
	{
		myConfigurable.reset();
	}

	@RequiredDispatchThread
	@Override
	public void disposeUIResources()
	{
		myConfigurable.disposeUIResources();
	}

	@Override
	public Configurable[] getConfigurables()
	{
		return myChildren.toArray(new Configurable[myChildren.size()]);
	}

	@Override
	@Nonnull
	public String getId()
	{
		return MavenSettings.class.getSimpleName();
	}

	@Override
	@Nls
	public String getDisplayName()
	{
		return DISPLAY_NAME;
	}

	@Override
	public String getHelpTopic()
	{
		return myConfigurable.getHelpTopic();
	}

	public static class MyMavenRunnerConfigurable extends MavenRunnerConfigurable
	{
		public MyMavenRunnerConfigurable(Project project)
		{
			super(project, false);
		}

		@Override
		protected MavenRunnerSettings getState()
		{
			return MavenRunner.getInstance(myProject).getState();
		}
	}
}