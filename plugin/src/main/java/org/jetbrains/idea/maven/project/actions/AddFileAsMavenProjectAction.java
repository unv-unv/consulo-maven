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

import java.util.Collections;

import javax.annotation.Nonnull;

import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import consulo.annotations.RequiredDispatchThread;

public class AddFileAsMavenProjectAction extends MavenAction
{
	@RequiredDispatchThread
	@Override
	public void actionPerformed(@Nonnull AnActionEvent e)
	{
		final DataContext context = e.getDataContext();
		MavenProjectsManager manager = MavenActionUtil.getProjectsManager(context);
		manager.addManagedFiles(Collections.singletonList(getSelectedFile(context)));
	}

	@Override
	protected boolean isAvailable(AnActionEvent e)
	{
		final DataContext context = e.getDataContext();
		VirtualFile file = getSelectedFile(context);
		return super.isAvailable(e) && MavenActionUtil.isMavenProjectFile(file) && !isExistingProjectFile(context, file);
	}

	@Override
	protected boolean isVisible(AnActionEvent e)
	{
		return super.isVisible(e) && isAvailable(e);
	}

	private static boolean isExistingProjectFile(DataContext context, VirtualFile file)
	{
		MavenProjectsManager manager = MavenActionUtil.getProjectsManager(context);
		return manager.findProject(file) != null;
	}

	@javax.annotation.Nullable
	private static VirtualFile getSelectedFile(DataContext context)
	{
		return context.getData(PlatformDataKeys.VIRTUAL_FILE);
	}
}