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
package org.jetbrains.idea.maven.wizards;

import consulo.application.ApplicationManager;
import consulo.application.ApplicationPropertiesComponent;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.maven.newProject.MavenNewModuleContext;
import consulo.maven.rt.server.common.model.MavenArchetype;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.project.Project;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.AsyncProcessIcon;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.speedSearch.SpeedSearchComparator;
import consulo.ui.ex.awt.speedSearch.TreeSpeedSearch;
import consulo.ui.ex.awt.tree.ColoredTreeCellRenderer;
import consulo.ui.ex.awt.tree.Tree;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.ui.ex.wizard.WizardStep;
import consulo.ui.ex.wizard.WizardStepValidationException;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.navigator.SelectMavenProjectDialog;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import jakarta.annotation.Nonnull;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.*;

public class MavenModuleWizardStep implements WizardStep<MavenNewModuleContext> {
    private static final String INHERIT_GROUP_ID_KEY = "MavenModuleWizard.inheritGroupId";
    private static final String INHERIT_VERSION_KEY = "MavenModuleWizard.inheritVersion";
    private static final String ARCHETYPE_ARTIFACT_ID_KEY = "MavenModuleWizard.archetypeArtifactIdKey";
    private static final String ARCHETYPE_GROUP_ID_KEY = "MavenModuleWizard.archetypeGroupIdKey";
    private static final String ARCHETYPE_VERSION_KEY = "MavenModuleWizard.archetypeVersionKey";

    private final Project myProjectOrNull;
    private final MavenNewModuleContext myContext;
    private MavenProject myAggregator;
    private MavenProject myParent;

    private String myInheritedGroupId;
    private String myInheritedVersion;

    private JPanel myMainPanel;

    private JTextField myGroupIdField;
    private JCheckBox myInheritGroupIdCheckBox;
    private JTextField myArtifactIdField;
    private JTextField myVersionField;
    private JCheckBox myInheritVersionCheckBox;

    private JCheckBox myUseArchetypeCheckBox;
    private JButton myAddArchetypeButton;
    private JScrollPane myArchetypesScrollPane;
    private JPanel myArchetypesPanel;
    private Tree myArchetypesTree;
    private JScrollPane myArchetypeDescriptionScrollPane;
    private JTextArea myArchetypeDescriptionField;

    private Object myCurrentUpdaterMarker;
    private final AsyncProcessIcon myLoadingIcon = new AsyncProcessIcon.Big(getClass() + ".loading");

    private boolean skipUpdateUI;

    public MavenModuleWizardStep(MavenNewModuleContext context) {
        myProjectOrNull = null;
        myContext = context;

        initComponents();
        loadSettings();
    }

    private void initComponents() {
        myArchetypesTree = new Tree();
        myArchetypesTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
        myArchetypesScrollPane = ScrollPaneFactory.createScrollPane(myArchetypesTree);

        myArchetypesPanel.add(myArchetypesScrollPane, "archetypes");

        JPanel loadingPanel = new JPanel(new GridBagLayout());
        JPanel bp = new JPanel(new BorderLayout(10, 10));
        bp.add(new JLabel("Loading archetype list..."), BorderLayout.NORTH);
        bp.add(myLoadingIcon, BorderLayout.CENTER);

        loadingPanel.add(bp, new GridBagConstraints());

        myArchetypesPanel.add(ScrollPaneFactory.createScrollPane(loadingPanel), "loading");
        ((CardLayout)myArchetypesPanel.getLayout()).show(myArchetypesPanel, "archetypes");

        ActionListener updatingListener = e -> updateComponents();
        myInheritGroupIdCheckBox.addActionListener(updatingListener);
        myInheritVersionCheckBox.addActionListener(updatingListener);

        myUseArchetypeCheckBox.addActionListener(updatingListener);
        myUseArchetypeCheckBox.addActionListener(e -> archetypeMayBeChanged());

        myAddArchetypeButton.addActionListener(e -> doAddArchetype());

        myArchetypesTree.setRootVisible(false);
        myArchetypesTree.setShowsRootHandles(true);
        myArchetypesTree.setCellRenderer(new MyRenderer());
        myArchetypesTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        myArchetypesTree.getSelectionModel().addTreeSelectionListener(e -> {
            updateArchetypeDescription();
            archetypeMayBeChanged();
        });

        new TreeSpeedSearch(myArchetypesTree, path -> {
            MavenArchetype info = getArchetypeInfoFromPathComponent(path.getLastPathComponent());
            return info.groupId + ":" + info.artifactId + ":" + info.version;
        }).setComparator(new SpeedSearchComparator(false));

        myArchetypeDescriptionField.setEditable(false);
        myArchetypeDescriptionField.setBackground(UIUtil.getPanelBackground());
    }

    private void archetypeMayBeChanged() {
        MavenArchetype selectedArchetype = getSelectedArchetype();
        if (((myContext.getArchetype() == null) != (selectedArchetype == null))) {
            myContext.setArchetype(selectedArchetype);
            skipUpdateUI = true;
            try {
                //fireStateChanged();
            }
            finally {
                skipUpdateUI = false;
            }
        }
    }

    @Override
    public JComponent getSwingPreferredFocusedComponent() {
        return myGroupIdField;
    }

    private MavenProject doSelectProject(MavenProject current) {
        assert myProjectOrNull != null : "must not be called when creating a new project";

        SelectMavenProjectDialog d = new SelectMavenProjectDialog(myProjectOrNull, current);
        d.show();
        if (!d.isOK()) {
            return current;
        }
        return d.getResult();
    }

    private void doAddArchetype() {
        MavenAddArchetypeDialog dialog = new MavenAddArchetypeDialog(myMainPanel);
        dialog.show();
        if (!dialog.isOK()) {
            return;
        }

        MavenArchetype archetype = dialog.getArchetype();
        MavenIndicesManager.getInstance().addArchetype(archetype);
        updateArchetypesList(archetype);
    }

    @Override
    public void onStepLeave(MavenNewModuleContext context) {
        saveSettings();

        updateDataModel();
    }

    public void updateDataModel() {
        // myContext.setProjectBuilder(myBuilder);
        myContext.setAggregatorProject(myAggregator);
        myContext.setParentProject(myParent);

        myContext.setProjectId(new MavenId(
            myGroupIdField.getText(),
            myArtifactIdField.getText(),
            myVersionField.getText()
        ));
        myContext.setInheritedOptions(
            myInheritGroupIdCheckBox.isSelected(),
            myInheritVersionCheckBox.isSelected()
        );

        myContext.setArchetype(getSelectedArchetype());
    }

    private void loadSettings() {
        myContext.setInheritedOptions(
            getSavedValue(INHERIT_GROUP_ID_KEY, true),
            getSavedValue(INHERIT_VERSION_KEY, true)
        );

        String archGroupId = getSavedValue(ARCHETYPE_GROUP_ID_KEY, null);
        String archArtifactId = getSavedValue(ARCHETYPE_ARTIFACT_ID_KEY, null);
        String archVersion = getSavedValue(ARCHETYPE_VERSION_KEY, null);
        if (archGroupId == null || archArtifactId == null || archVersion == null) {
            myContext.setArchetype(null);
        }
        else {
            myContext.setArchetype(new MavenArchetype(archGroupId, archArtifactId, archVersion, null, null));
        }
    }

    private void saveSettings() {
        saveValue(INHERIT_GROUP_ID_KEY, myInheritGroupIdCheckBox.isSelected());
        saveValue(INHERIT_VERSION_KEY, myInheritVersionCheckBox.isSelected());

        MavenArchetype arch = getSelectedArchetype();
        saveValue(ARCHETYPE_GROUP_ID_KEY, arch == null ? null : arch.groupId);
        saveValue(ARCHETYPE_ARTIFACT_ID_KEY, arch == null ? null : arch.artifactId);
        saveValue(ARCHETYPE_VERSION_KEY, arch == null ? null : arch.version);
    }

    private boolean getSavedValue(String key, boolean defaultValue) {
        return getSavedValue(key, String.valueOf(defaultValue)).equals(String.valueOf(true));
    }

    private static String getSavedValue(String key, String defaultValue) {
        String value = ApplicationPropertiesComponent.getInstance().getValue(key);
        return value == null ? defaultValue : value;
    }

    private void saveValue(String key, boolean value) {
        saveValue(key, String.valueOf(value));
    }

    private static void saveValue(String key, String value) {
        ApplicationPropertiesComponent props = ApplicationPropertiesComponent.getInstance();
        props.setValue(key, value);
    }

    @RequiredUIAccess
    @Nonnull
    @Override
    public Component getComponent(@Nonnull MavenNewModuleContext context, @Nonnull Disposable uiDisposable) {
        throw new UnsupportedOperationException("desktop only");
    }

    @Nonnull
    @Override
    public JComponent getSwingComponent(@Nonnull MavenNewModuleContext context, @Nonnull Disposable uiDisposable) {
        Disposer.register(uiDisposable, myLoadingIcon);
        return myMainPanel;
    }

    @Override
    public void validateStep(@Nonnull MavenNewModuleContext context) throws WizardStepValidationException {
        if (StringUtil.isEmptyOrSpaces(myGroupIdField.getText())) {
            throw new WizardStepValidationException("Please, specify groupId");
        }

        if (StringUtil.isEmptyOrSpaces(myArtifactIdField.getText())) {
            throw new WizardStepValidationException("Please, specify artifactId");
        }

        if (StringUtil.isEmptyOrSpaces(myVersionField.getText())) {
            throw new WizardStepValidationException("Please, specify version");
        }
    }

    @Override
    public void onStepEnter(@Nonnull MavenNewModuleContext context) {
        updateStep();
    }

    public void updateStep() {
        if (skipUpdateUI) {
            return;
        }

        if (isMavenizedProject()) {
            MavenProject parent = myContext.findPotentialParentProject(myProjectOrNull);
            myAggregator = parent;
            myParent = parent;
        }

        MavenId projectId = myContext.getProjectId();

        if (projectId == null) {
            myArtifactIdField.setText(myContext.getName());
            myGroupIdField.setText(myParent == null ? myContext.getName() : myParent.getMavenId().getGroupId());
            myVersionField.setText(myParent == null ? "1.0-SNAPSHOT" : myParent.getMavenId().getVersion());
        }
        else {
            myArtifactIdField.setText(projectId.getArtifactId());
            myGroupIdField.setText(projectId.getGroupId());
            myVersionField.setText(projectId.getVersion());
        }

        myInheritGroupIdCheckBox.setSelected(myContext.isInheritGroupId());
        myInheritVersionCheckBox.setSelected(myContext.isInheritVersion());

        MavenArchetype selectedArch = getSelectedArchetype();
        if (selectedArch == null) {
            selectedArch = myContext.getArchetype();
        }
        if (selectedArch != null) {
            myUseArchetypeCheckBox.setSelected(true);
        }

        if (myArchetypesTree.getRowCount() == 0) {
            updateArchetypesList(selectedArch);
        }
        updateComponents();
    }

    private void updateArchetypesList(final MavenArchetype selected) {
        ApplicationManager.getApplication().assertIsDispatchThread();

        myLoadingIcon.setBackground(myArchetypesTree.getBackground());

        ((CardLayout)myArchetypesPanel.getLayout()).show(myArchetypesPanel, "loading");

        final Object currentUpdaterMarker = new Object();
        myCurrentUpdaterMarker = currentUpdaterMarker;

        ApplicationManager.getApplication().executeOnPooledThread((Runnable)() -> {
            final Set<MavenArchetype> archetypes = MavenIndicesManager.getInstance().getArchetypes();

            SwingUtilities.invokeLater(() -> {
                if (currentUpdaterMarker != myCurrentUpdaterMarker) {
                    return; // Other updater has been run.
                }

                ((CardLayout)myArchetypesPanel.getLayout()).show(myArchetypesPanel, "archetypes");

                TreeNode root = groupAndSortArchetypes(archetypes);
                TreeModel model = new DefaultTreeModel(root);
                myArchetypesTree.setModel(model);

                if (selected != null) {
                    TreePath path = findNodePath(selected, model, model.getRoot());
                    if (path != null) {
                        myArchetypesTree.expandPath(path.getParentPath());
                        TreeUtil.selectPath(myArchetypesTree, path, true);
                    }
                }

                updateArchetypeDescription();
            });
        });
    }

    private void updateArchetypeDescription() {
        MavenArchetype sel = getSelectedArchetype();
        String desc = sel == null ? null : sel.description;
        if (StringUtil.isEmptyOrSpaces(desc)) {
            myArchetypeDescriptionScrollPane.setVisible(false);
        }
        else {
            myArchetypeDescriptionScrollPane.setVisible(true);
            myArchetypeDescriptionField.setText(desc);
        }
        myMainPanel.revalidate();
    }

    @Nullable
    private static TreePath findNodePath(MavenArchetype object, TreeModel model, Object parent) {
        for (int i = 0; i < model.getChildCount(parent); i++) {
            DefaultMutableTreeNode each = (DefaultMutableTreeNode)model.getChild(parent, i);
            if (each.getUserObject().equals(object)) {
                return new TreePath(each.getPath());
            }

            TreePath result = findNodePath(object, model, each);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static TreeNode groupAndSortArchetypes(Set<MavenArchetype> archetypes) {
        List<MavenArchetype> list = new ArrayList<>(archetypes);

        Collections.sort(list, (o1, o2) -> {
            String key1 = o1.groupId + ":" + o1.artifactId;
            String key2 = o2.groupId + ":" + o2.artifactId;

            int result = key1.compareToIgnoreCase(key2);
            if (result != 0) {
                return result;
            }

            return o2.version.compareToIgnoreCase(o1.version);
        });

        Map<String, List<MavenArchetype>> map = new TreeMap<>();

        for (MavenArchetype each : list) {
            String key = each.groupId + ":" + each.artifactId;
            List<MavenArchetype> versions = map.get(key);
            if (versions == null) {
                versions = new ArrayList<>();
                map.put(key, versions);
            }
            versions.add(each);
        }

        DefaultMutableTreeNode result = new DefaultMutableTreeNode("root", true);
        for (List<MavenArchetype> each : map.values()) {
            MavenArchetype eachArchetype = each.get(0);
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(eachArchetype, true);
            for (MavenArchetype eachVersion : each) {
                DefaultMutableTreeNode versionNode = new DefaultMutableTreeNode(eachVersion, false);
                node.add(versionNode);
            }
            result.add(node);
        }

        return result;
    }

    private boolean isMavenizedProject() {
        return myProjectOrNull != null && MavenProjectsManager.getInstance(myProjectOrNull).isMavenizedProject();
    }

    private void updateComponents() {
        if (myParent == null) {
            myGroupIdField.setEnabled(true);
            myVersionField.setEnabled(true);
            myInheritGroupIdCheckBox.setEnabled(false);
            myInheritVersionCheckBox.setEnabled(false);
        }
        else {
            myGroupIdField.setEnabled(!myInheritGroupIdCheckBox.isSelected());
            myVersionField.setEnabled(!myInheritVersionCheckBox.isSelected());

            if (myInheritGroupIdCheckBox.isSelected()
                || myGroupIdField.getText().equals(myInheritedGroupId)) {
                myGroupIdField.setText(myParent.getMavenId().getGroupId());
            }
            if (myInheritVersionCheckBox.isSelected()
                || myVersionField.getText().equals(myInheritedVersion)) {
                myVersionField.setText(myParent.getMavenId().getVersion());
            }
            myInheritedGroupId = myGroupIdField.getText();
            myInheritedVersion = myVersionField.getText();

            myInheritGroupIdCheckBox.setEnabled(true);
            myInheritVersionCheckBox.setEnabled(true);
        }

        boolean archetypesEnabled = myUseArchetypeCheckBox.isSelected();
        myAddArchetypeButton.setEnabled(archetypesEnabled);
        myArchetypesTree.setEnabled(archetypesEnabled);
        myArchetypesTree.setBackground(archetypesEnabled ? UIUtil.getListBackground() : UIUtil.getPanelBackground());
    }

    private static String formatProjectString(MavenProject project) {
        if (project == null) {
            return "<none>";
        }
        return project.getMavenId().getDisplayString();
    }

    @Nullable
    private MavenArchetype getSelectedArchetype() {
        if (!myUseArchetypeCheckBox.isSelected() || myArchetypesTree.isSelectionEmpty()) {
            return null;
        }
        return getArchetypeInfoFromPathComponent(myArchetypesTree.getLastSelectedPathComponent());
    }

    private static MavenArchetype getArchetypeInfoFromPathComponent(Object sel) {
        return (MavenArchetype)((DefaultMutableTreeNode)sel).getUserObject();
    }

    private static class MyRenderer extends ColoredTreeCellRenderer {
        public void customizeCellRenderer(
            JTree tree,
            Object value,
            boolean selected,
            boolean expanded,
            boolean leaf,
            int row,
            boolean hasFocus
        ) {
            Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
            if (!(userObject instanceof MavenArchetype)) {
                return;
            }

            MavenArchetype info = (MavenArchetype)userObject;

            if (leaf) {
                append(info.artifactId, SimpleTextAttributes.GRAY_ATTRIBUTES);
                append(":" + info.version, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
            else {
                append(info.groupId + ":", SimpleTextAttributes.GRAY_ATTRIBUTES);
                append(info.artifactId, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
        }
    }
}

