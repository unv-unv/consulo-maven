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
package org.jetbrains.idea.maven.utils;

import consulo.application.Application;
import consulo.component.PropertiesComponent;
import consulo.disposer.Disposer;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserDialog;
import consulo.maven.rt.server.common.model.MavenArtifactInfo;
import consulo.maven.rt.server.common.model.MavenRepositoryInfo;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.project.ProjectPropertiesComponent;
import consulo.project.localize.ProjectLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.Predicates;
import consulo.util.lang.xml.XmlStringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.idea.maven.utils.library.RepositoryAttachHandler;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.*;

public class RepositoryAttachDialog extends DialogWrapper {
    private static final String PROPERTY_DOWNLOAD_TO_PATH = "Downloaded.Files.Path";
    private static final String PROPERTY_ATTACH_JAVADOC = "Repository.Attach.JavaDocs";
    private static final String PROPERTY_ATTACH_SOURCES = "Repository.Attach.Sources";

    private JBLabel myInfoLabel;
    private JCheckBox myJavaDocCheckBox;
    private JCheckBox mySourcesCheckBox;
    private final Project myProject;
    private final boolean myManaged;
    private AsyncProcessIcon myProgressIcon;
    private ComboboxWithBrowseButton myComboComponent;
    private JPanel myPanel;
    private JBLabel myCaptionLabel;
    private final Map<String, Pair<MavenArtifactInfo, MavenRepositoryInfo>> myCoordinates = new HashMap<>();
    private final Map<String, MavenRepositoryInfo> myRepositories = new TreeMap<>();
    private final List<String> myShownItems = new ArrayList<>();
    private final JComboBox myCombobox;

    private TextFieldWithBrowseButton myDirectoryField;
    private String myFilterString;
    private boolean myInUpdate;

    public RepositoryAttachDialog(Project project, boolean managed, final @Nullable String initialFilter) {
        super(project, true);
        myProject = project;
        myManaged = managed;
        myProgressIcon.suspend();
        myCaptionLabel.setText(
            XmlStringUtil.wrapInHtml(StringUtil.escapeXml("enter keyword, pattern or class name to search by or Maven coordinates," +
                "i.e. 'springframework', 'Logger' or 'org.hibernate:hibernate-core:3.5.0.GA':")
            ));
        myInfoLabel.setPreferredSize(new Dimension(
            myInfoLabel.getFontMetrics(myInfoLabel.getFont()).stringWidth("Showing: 1000"),
            myInfoLabel.getPreferredSize().height
        ));

        myComboComponent.setButtonIcon(PlatformIconGroup.actionsSearch());
        myComboComponent.getButton().addActionListener(e -> performSearch());
        myCombobox = myComboComponent.getComboBox();
        myCombobox.setModel(new CollectionComboBoxModel(myShownItems, null));
        myCombobox.setEditable(true);
        final JTextField textField = (JTextField)myCombobox.getEditor().getEditorComponent();
        textField.setColumns(20);
        if (initialFilter != null) {
            textField.setText(initialFilter);
        }
        textField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                Application.get().invokeLater(() -> {
                    if (myProgressIcon.isDisposed()) {
                        return;
                    }
                    updateComboboxSelection(false);
                });
            }
        });
        myCombobox.addActionListener(e -> {
            final boolean popupVisible = myCombobox.isPopupVisible();
            if (!myInUpdate && (!popupVisible || myCoordinates.isEmpty())) {
                performSearch();
            }
            else {
                final String item = (String)myCombobox.getSelectedItem();
                if (StringUtil.isNotEmpty(item)) {
                    ((JTextField)myCombobox.getEditor().getEditorComponent()).setText(item);
                }
            }
        });
        final PropertiesComponent storage = ProjectPropertiesComponent.getInstance(myProject);
        final boolean pathValueSet = storage.isValueSet(PROPERTY_DOWNLOAD_TO_PATH);
        if (pathValueSet) {
            myDirectoryField.setText(storage.getValue(PROPERTY_DOWNLOAD_TO_PATH));
        }
        myJavaDocCheckBox.setSelected(storage.isValueSet(PROPERTY_ATTACH_JAVADOC) && storage.isTrueValue(PROPERTY_ATTACH_JAVADOC));
        mySourcesCheckBox.setSelected(storage.isValueSet(PROPERTY_ATTACH_SOURCES) && storage.isTrueValue(PROPERTY_ATTACH_SOURCES));
        if (!myManaged) {
            if (!pathValueSet && myProject != null && !myProject.isDefault()) {
                final VirtualFile baseDir = myProject.getBaseDir();
                if (baseDir != null) {
                    myDirectoryField.setText(FileUtil.toSystemDependentName(baseDir.getPath() + "/lib"));
                }
            }
            final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
            descriptor.putUserData(FileChooserDialog.PREFER_LAST_OVER_TO_SELECT, Boolean.TRUE);
            myDirectoryField.addBrowseFolderListener(
                ProjectLocalize.fileChooserDirectoryForDownloadedLibrariesTitle().get(),
                ProjectLocalize.fileChooserDirectoryForDownloadedLibrariesDescription().get(),
                null,
                descriptor
            );
        }
        else {
            myDirectoryField.setVisible(false);
        }
        updateInfoLabel();
        init();
    }

    public boolean getAttachJavaDoc() {
        return myJavaDocCheckBox.isSelected();
    }

    public boolean getAttachSources() {
        return mySourcesCheckBox.isSelected();
    }

    public String getDirectoryPath() {
        return myDirectoryField.getText();
    }

    @Override
    @RequiredUIAccess
    public JComponent getPreferredFocusedComponent() {
        return myCombobox;
    }

    private void updateComboboxSelection(boolean force) {
        final String prevFilter = myFilterString;
        final JTextComponent field = (JTextComponent)myCombobox.getEditor().getEditorComponent();
        final int caret = field.getCaretPosition();
        myFilterString = field.getText();

        if (!force && Comparing.equal(myFilterString, prevFilter)) {
            return;
        }
        int prevSize = myShownItems.size();
        myShownItems.clear();

        myInUpdate = true;
        final boolean itemSelected = myCoordinates.containsKey(myFilterString) &&
            Comparing.strEqual((String)myCombobox.getSelectedItem(), myFilterString, false);
        final boolean filtered;
        if (itemSelected) {
            myShownItems.addAll(myCoordinates.keySet());
            filtered = false;
        }
        else {
            final String[] parts = myFilterString.split(" ");
            main:
            for (String coordinate : myCoordinates.keySet()) {
                for (String part : parts) {
                    if (!StringUtil.containsIgnoreCase(coordinate, part)) {
                        continue main;
                    }
                }
                myShownItems.add(coordinate);
            }
            filtered = !myShownItems.isEmpty();
            if (!filtered) {
                myShownItems.addAll(myCoordinates.keySet());
            }
            myCombobox.setSelectedItem(null);
        }
        Collections.sort(myShownItems);
        ((CollectionComboBoxModel)myCombobox.getModel()).update();
        myInUpdate = false;
        field.setText(myFilterString);
        field.setCaretPosition(caret);
        updateInfoLabel();
        if (filtered) {
            if (prevSize < 10 && myShownItems.size() > prevSize && myCombobox.isPopupVisible()) {
                myCombobox.setPopupVisible(false);
            }
            if (!myCombobox.isPopupVisible()) {
                myCombobox.setPopupVisible(filtered);
            }
        }
    }

    private boolean performSearch() {
        final String text = getCoordinateText();
        if (myCoordinates.containsKey(text)) {
            return false;
        }
        if (myProgressIcon.isRunning()) {
            return false;
        }
        myProgressIcon.resume();
        RepositoryAttachHandler.searchArtifacts(
            myProject,
            text,
            (artifacts, tooMany) -> {
                if (myProgressIcon.isDisposed()) {
                    return false;
                }
                if (tooMany != null) {
                    myProgressIcon.suspend(); // finished
                }
                final int prevSize = myCoordinates.size();
                for (Pair<MavenArtifactInfo, MavenRepositoryInfo> each : artifacts) {
                    myCoordinates.put(each.first.getGroupId() + ":" + each.first.getArtifactId() + ":" + each.first.getVersion(), each);
                    String url = each.second != null ? each.second.getUrl() : null;
                    if (StringUtil.isNotEmpty(url) && !myRepositories.containsKey(url)) {
                        myRepositories.put(url, each.second);
                    }
                }
                String title = getTitle();
                String tooManyMessage = ": too many results found";
                if (tooMany != null) {
                    boolean alreadyThere = title.endsWith(tooManyMessage);
                    if (tooMany && !alreadyThere) {
                        setTitle(title + tooManyMessage);
                    }
                    else if (!tooMany && alreadyThere) {
                        setTitle(title.substring(0, title.length() - tooManyMessage.length()));
                    }
                }
                updateComboboxSelection(prevSize != myCoordinates.size());
                return true;
            }
        );
        return true;
    }

    private void updateInfoLabel() {
        myInfoLabel.setText("<html>Found: " + myCoordinates.size() + "<br>Showing: " + myCombobox.getModel().getSize() + "</html>");
    }

    @Override
    @RequiredUIAccess
    protected ValidationInfo doValidate() {
        if (!isValidCoordinateSelected()) {
            return new ValidationInfo("Please enter valid coordinate, discover it or select one from the list", myCombobox);
        }
        else if (!myManaged) {
            final File dir = new File(myDirectoryField.getText());
            if (!dir.exists() && !dir.mkdirs() || !dir.isDirectory()) {
                return new ValidationInfo("Please enter valid library files path", myDirectoryField.getTextField());
            }
        }
        return super.doValidate();
    }

    @Override
    protected JComponent createCenterPanel() {
        return null;
    }

    @Override
    protected JComponent createNorthPanel() {
        return myPanel;
    }

    @Override
    protected void dispose() {
        Disposer.dispose(myProgressIcon);
        final PropertiesComponent storage = ProjectPropertiesComponent.getInstance(myProject);
        storage.setValue(PROPERTY_DOWNLOAD_TO_PATH, myDirectoryField.getText());
        storage.setValue(PROPERTY_ATTACH_JAVADOC, String.valueOf(myJavaDocCheckBox.isSelected()));
        storage.setValue(PROPERTY_ATTACH_SOURCES, String.valueOf(mySourcesCheckBox.isSelected()));
        super.dispose();
    }

    @Override
    protected String getDimensionServiceKey() {
        return RepositoryAttachDialog.class.getName();
    }

    @Nonnull
    public List<MavenRepositoryInfo> getRepositories() {
        final Pair<MavenArtifactInfo, MavenRepositoryInfo> artifactAndRepo = myCoordinates.get(getCoordinateText());
        final MavenRepositoryInfo repository = artifactAndRepo == null ? null : artifactAndRepo.second;
        return repository != null
            ? Collections.singletonList(repository)
            : ContainerUtil.findAll(myRepositories.values(), Predicates.notNull());
    }

    private boolean isValidCoordinateSelected() {
        final String text = getCoordinateText();
        return text.split(":").length == 3;
    }

    public String getCoordinateText() {
        final JTextField field = (JTextField)myCombobox.getEditor().getEditorComponent();
        return field.getText();
    }

    private void createUIComponents() {
        myProgressIcon = new AsyncProcessIcon("Progress");
    }
}
