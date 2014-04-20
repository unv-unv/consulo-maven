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
package org.jetbrains.idea.maven.importing;

import java.util.List;
import java.util.Map;

import org.consulo.java.platform.module.extension.JavaMutableModuleExtensionImpl;
import org.consulo.maven.module.extension.MavenMutableModuleExtension;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenImportingSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.project.SupportedRequestType;
import org.jetbrains.idea.maven.utils.MavenUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTable;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleExtensionWithSdkOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.util.ArchiveVfsUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.containers.ContainerUtil;
import lombok.val;

public class MavenModuleImporter
{

	public static final String SUREFIRE_PLUGIN_LIBRARY_NAME = "maven-surefire-plugin urls";

	private final Module myModule;
	private final MavenProjectsTree myMavenTree;
	private final MavenProject myMavenProject;

	@Nullable
	private final MavenProjectChanges myMavenProjectChanges;
	private final Map<MavenProject, String> myMavenProjectToModuleName;
	private final MavenImportingSettings mySettings;
	private final MavenModifiableModelsProvider myModifiableModelsProvider;
	private MavenRootModelAdapter myRootModelAdapter;

	public MavenModuleImporter(
			Module module,
			MavenProjectsTree mavenTree,
			MavenProject mavenProject,
			@Nullable MavenProjectChanges changes,
			Map<MavenProject, String> mavenProjectToModuleName,
			MavenImportingSettings settings,
			MavenModifiableModelsProvider modifiableModelsProvider)
	{
		myModule = module;
		myMavenTree = mavenTree;
		myMavenProject = mavenProject;
		myMavenProjectChanges = changes;
		myMavenProjectToModuleName = mavenProjectToModuleName;
		mySettings = settings;
		myModifiableModelsProvider = modifiableModelsProvider;
	}

	public ModifiableRootModel getRootModel()
	{
		return myRootModelAdapter.getRootModel();
	}

	public void config(boolean isNewlyCreatedModule)
	{
		myRootModelAdapter = new MavenRootModelAdapter(myMavenProject, myModule, myModifiableModelsProvider);
		myRootModelAdapter.init(isNewlyCreatedModule);

		val rootModel = myModifiableModelsProvider.getRootModel(myModule);

		JavaMutableModuleExtensionImpl javaModuleExtension = rootModel.getExtensionWithoutCheck(JavaMutableModuleExtensionImpl.class);
		javaModuleExtension.setEnabled(true);

		val languageLevel = LanguageLevel.parse(myMavenProject.getSourceLevel());

		List<Sdk> sdksOfType = SdkTable.getInstance().getSdksOfType(JavaSdk.getInstance());

		LanguageLevel targetLevel = null;
		Sdk targetSdk = null;
		if(languageLevel != null)
		{
			targetLevel = languageLevel;

			targetSdk = ContainerUtil.find(sdksOfType, new Condition<Sdk>()
			{
				@Override
				public boolean value(Sdk sdk)
				{
					JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
					return version != null && version.getMaxLanguageLevel().isAtLeast(languageLevel);
				}
			});
		}
		else
		{
			Sdk bundleSdkByType = SdkTable.getInstance().findBundleSdkByType(JavaSdk.class);
			if(bundleSdkByType == null)
			{
				bundleSdkByType = ContainerUtil.getFirstItem(sdksOfType);
			}

			if(bundleSdkByType != null)
			{
				targetSdk = bundleSdkByType;
				JavaSdkVersion version = JavaSdk.getInstance().getVersion(targetSdk);
				targetLevel = version != null ? version.getMaxLanguageLevel() : LanguageLevel.HIGHEST;
			}
			else
			{
				targetLevel = LanguageLevel.HIGHEST;
			}
		}

		javaModuleExtension.getInheritableSdk().set(null, targetSdk);
		javaModuleExtension.getInheritableLanguageLevel().set(null, targetLevel);

		ModuleExtensionWithSdkOrderEntry moduleExtensionSdkEntry = rootModel.findModuleExtensionSdkEntry(javaModuleExtension);
		if(moduleExtensionSdkEntry == null)
		{
			rootModel.addModuleExtensionSdkEntry(javaModuleExtension);
		}
		rootModel.getExtensionWithoutCheck(MavenMutableModuleExtension.class).setEnabled(true);

		configFolders();
		configDependencies();
	}

	public void preConfigFacets()
	{
		MavenUtil.invokeAndWaitWriteAction(myModule.getProject(), new Runnable()
		{
			public void run()
			{
				if(myModule.isDisposed())
				{
					return;
				}

				for(final MavenImporter importer : getSuitableImporters())
				{
					final MavenProjectChanges changes;
					if(myMavenProjectChanges == null)
					{
						if(importer.processChangedModulesOnly())
						{
							continue;
						}
						changes = MavenProjectChanges.NONE;
					}
					else
					{
						changes = myMavenProjectChanges;
					}

					importer.preProcess(myModule, myMavenProject, changes, myModifiableModelsProvider);
				}
			}
		});
	}

	public void configFacets(final List<MavenProjectsProcessorTask> postTasks)
	{
		MavenUtil.invokeAndWaitWriteAction(myModule.getProject(), new Runnable()
		{
			public void run()
			{
				if(myModule.isDisposed())
				{
					return;
				}

				for(final MavenImporter importer : getSuitableImporters())
				{
					final MavenProjectChanges changes;
					if(myMavenProjectChanges == null)
					{
						if(importer.processChangedModulesOnly())
						{
							continue;
						}
						changes = MavenProjectChanges.NONE;
					}
					else
					{
						changes = myMavenProjectChanges;
					}

					importer.process(myModifiableModelsProvider, myModule, myRootModelAdapter, myMavenTree, myMavenProject, changes,
							myMavenProjectToModuleName, postTasks);
				}
			}
		});
	}

	private List<MavenImporter> getSuitableImporters()
	{
		return myMavenProject.getSuitableImporters();
	}

	private void configFolders()
	{
		new MavenFoldersImporter(myMavenProject, mySettings, myRootModelAdapter).config();
	}

	private void configDependencies()
	{
		for(MavenArtifact artifact : myMavenProject.getDependencies())
		{
			if(!myMavenProject.isSupportedDependency(artifact, SupportedRequestType.FOR_IMPORT))
			{
				continue;
			}

			DependencyScope scope = selectScope(artifact.getScope());

			MavenProject depProject = myMavenTree.findProject(artifact.getMavenId());

			if(depProject != null && !myMavenTree.isIgnored(depProject))
			{
				if(depProject == myMavenProject)
				{
					continue;
				}
				boolean isTestJar = MavenConstants.TYPE_TEST_JAR.equals(artifact.getType()) || "tests".equals(artifact.getClassifier());
				myRootModelAdapter.addModuleDependency(myMavenProjectToModuleName.get(depProject), scope, isTestJar);

				Element buildHelperCfg = depProject.getPluginGoalConfiguration("org.codehaus.mojo", "build-helper-maven-plugin", "attach-artifact");
				if(buildHelperCfg != null)
				{
					addAttachArtifactDependency(buildHelperCfg, scope, depProject, artifact);
				}

				if(artifact.getClassifier() != null && !"system".equals(artifact.getScope()) && !"false".equals(System.getProperty("idea.maven" + "" +
						".classifier.dep")))
				{
					MavenArtifact a = new MavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(),
							artifact.getBaseVersion(), artifact.getType(), artifact.getClassifier(), artifact.getScope(), artifact.isOptional(),
							artifact.getExtension(), null, myMavenProject.getLocalRepository(), false, false);

					myRootModelAdapter.addLibraryDependency(a, scope, myModifiableModelsProvider, myMavenProject);
				}
			}
			else if("system".equals(artifact.getScope()))
			{
				myRootModelAdapter.addSystemDependency(artifact, scope);
			}
			else
			{
				myRootModelAdapter.addLibraryDependency(artifact, scope, myModifiableModelsProvider, myMavenProject);
			}
		}

		configSurefirePlugin();
	}

	private void configSurefirePlugin()
	{
		// Remove "maven-surefire-plugin urls" library created by previous version of IDEA.
		// todo remove this code after 01.06.2013
		LibraryTable moduleLibraryTable = myRootModelAdapter.getRootModel().getModuleLibraryTable();

		Library library = moduleLibraryTable.getLibraryByName(SUREFIRE_PLUGIN_LIBRARY_NAME);
		if(library != null)
		{
			moduleLibraryTable.removeLibrary(library);
		}
	}

	private void addAttachArtifactDependency(
			@NotNull Element buildHelperCfg, @NotNull DependencyScope scope, @NotNull MavenProject mavenProject, @NotNull MavenArtifact artifact)
	{
		Library.ModifiableModel libraryModel = null;

		for(Element artifactsElement : (List<Element>) buildHelperCfg.getChildren("artifacts"))
		{
			for(Element artifactElement : (List<Element>) artifactsElement.getChildren("artifact"))
			{
				String typeString = artifactElement.getChildTextTrim("type");
				if(typeString != null && !typeString.equals("jar"))
				{
					continue;
				}

				OrderRootType rootType = OrderRootType.CLASSES;

				String classifier = artifactElement.getChildTextTrim("classifier");
				if("sources".equals(classifier))
				{
					rootType = OrderRootType.SOURCES;
				}
				else if("javadoc".equals(classifier))
				{
					rootType = OrderRootType.DOCUMENTATION;
				}

				String filePath = artifactElement.getChildTextTrim("file");
				if(StringUtil.isEmpty(filePath))
				{
					continue;
				}

				VirtualFile file = VfsUtil.findRelativeFile(filePath, mavenProject.getDirectoryFile());
				if(file == null)
				{
					continue;
				}

				file = ArchiveVfsUtil.getJarRootForLocalFile(file);
				if(file == null)
				{
					continue;
				}

				if(libraryModel == null)
				{
					String libraryName = artifact.getLibraryName();
					assert libraryName.startsWith(MavenArtifact.MAVEN_LIB_PREFIX);
					libraryName = MavenArtifact.MAVEN_LIB_PREFIX + "ATTACHED-JAR: " + libraryName.substring(MavenArtifact.MAVEN_LIB_PREFIX.length());

					Library library = myModifiableModelsProvider.getLibraryByName(libraryName);
					if(library == null)
					{
						library = myModifiableModelsProvider.createLibrary(libraryName);
					}
					libraryModel = myModifiableModelsProvider.getLibraryModel(library);

					LibraryOrderEntry entry = myRootModelAdapter.getRootModel().addLibraryEntry(library);
					entry.setScope(scope);
				}

				libraryModel.addRoot(file, rootType);
			}
		}
	}

	@NotNull
	public static DependencyScope selectScope(String mavenScope)
	{
		if(MavenConstants.SCOPE_RUNTIME.equals(mavenScope))
		{
			return DependencyScope.RUNTIME;
		}
		if(MavenConstants.SCOPE_TEST.equals(mavenScope))
		{
			return DependencyScope.TEST;
		}
		if(MavenConstants.SCOPE_PROVIDEED.equals(mavenScope))
		{
			return DependencyScope.PROVIDED;
		}
		return DependencyScope.COMPILE;
	}
}
