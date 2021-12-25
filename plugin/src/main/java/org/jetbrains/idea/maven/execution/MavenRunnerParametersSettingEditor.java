package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import consulo.ui.annotation.RequiredUIAccess;

import javax.annotation.Nonnull;
import javax.swing.*;

/**
 * @author Sergey Evdokimov
 */
public class MavenRunnerParametersSettingEditor extends SettingsEditor<MavenRunConfiguration>
{
	private final MavenRunnerParametersPanel myPanel;
	private BorderLayoutPanel myVerticalPanel;

	@RequiredUIAccess
	public MavenRunnerParametersSettingEditor(@Nonnull Project project)
	{
		myPanel = new MavenRunnerParametersPanel(project);
	}

	@Override
	protected void resetEditorFrom(MavenRunConfiguration runConfiguration)
	{
		myPanel.getData(runConfiguration.getRunnerParameters());
	}

	@Override
	protected void applyEditorTo(MavenRunConfiguration runConfiguration) throws ConfigurationException
	{
		myPanel.setData(runConfiguration.getRunnerParameters());
	}

	@Nonnull
	@Override
	protected JComponent createEditor()
	{
		if(myVerticalPanel != null)
		{
			return myVerticalPanel;
		}
		myVerticalPanel = new BorderLayoutPanel();
		myVerticalPanel.setBorder(JBUI.Borders.empty(5, 0, 0, 0));
		myVerticalPanel.addToTop(myPanel.createComponent());
		return myVerticalPanel;
	}

	@Override
	protected void disposeEditor()
	{
		myPanel.disposeUIResources();
	}
}
