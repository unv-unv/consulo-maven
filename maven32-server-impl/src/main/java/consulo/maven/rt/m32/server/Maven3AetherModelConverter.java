/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package consulo.maven.rt.m32.server;

import consulo.maven.rt.m3.common.server.MavenModelConverter;
import consulo.maven.rt.server.common.model.*;
import consulo.maven.rt.server.common.util.MavenStringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

import java.io.File;
import java.rmi.RemoteException;
import java.util.*;

/**
 * {@link Maven3AetherModelConverter} provides adapted methods of {@link MavenModelConverter} for aether models conversion
 *
 * @author Vladislav.Soroka
 * @since 9/24/2014
 */
public class Maven3AetherModelConverter extends MavenModelConverter
{
	@Nonnull
	public static MavenModel convertModelWithAetherDependencyTree(Model model,
																  List<String> sources,
																  List<String> testSources,
																  Collection<Artifact> dependencies,
																  Collection<DependencyNode> dependencyTree,
																  Collection<Artifact> extensions,
																  File localRepository) throws RemoteException
	{
		MavenModel result = new MavenModel();
		result.setMavenId(new MavenId(model.getGroupId(), model.getArtifactId(), model.getVersion()));

		Parent parent = model.getParent();
		if(parent != null)
		{
			result.setParent(new MavenParent(new MavenId(parent.getGroupId(), parent.getArtifactId(), parent.getVersion()), parent.getRelativePath()));
		}
		result.setPackaging(model.getPackaging());
		result.setName(model.getName());
		result.setProperties(model.getProperties() == null ? new Properties() : model.getProperties());
		result.setPlugins(convertPlugins(model));

		Map<Artifact, MavenArtifact> convertedArtifacts = new HashMap<Artifact, MavenArtifact>();
		result.setExtensions(convertArtifacts(extensions, convertedArtifacts, localRepository));
		result.setDependencyTree(convertAetherDependencyNodes(null, dependencyTree, convertedArtifacts, localRepository));
		result.setDependencies(convertArtifacts(dependencies, convertedArtifacts, localRepository));

		result.setRemoteRepositories(convertRepositories(model.getRepositories()));
		result.setProfiles(convertProfiles(model.getProfiles()));
		result.setModules(model.getModules());

		convertBuild(result.getBuild(), model.getBuild(), sources, testSources);
		return result;
	}

	public static List<MavenArtifactNode> convertAetherDependencyNodes(MavenArtifactNode parent,
																	   Collection<DependencyNode> nodes,
																	   Map<Artifact, MavenArtifact> nativeToConvertedMap,
																	   File localRepository)
	{
		List<MavenArtifactNode> result = new ArrayList<MavenArtifactNode>(nodes.size());
		for(DependencyNode each : nodes)
		{
			Artifact a = toArtifact(each.getDependency());
			MavenArtifact ma = convertArtifact(a, nativeToConvertedMap, localRepository);

			Map<?, ?> data = each.getData();
			String premanagedVersion = DependencyManagerUtils.getPremanagedVersion(each);
			String premanagedScope = DependencyManagerUtils.getPremanagedScope(each);


			MavenArtifactState state = MavenArtifactState.ADDED;
			MavenArtifact relatedArtifact = null;

			String scope = each.getDependency().getScope();
			Object winner = data.get(ConflictResolver.NODE_DATA_WINNER);
			if(winner instanceof DependencyNode)
			{
				DependencyNode winnerNode = (DependencyNode) winner;
				scope = winnerNode.getDependency().getScope();
				Artifact winnerArtifact = toArtifact(winnerNode.getDependency());
				relatedArtifact = convertArtifact(winnerArtifact, nativeToConvertedMap, localRepository);
				nativeToConvertedMap.put(a, relatedArtifact);
				if(!MavenStringUtil.equal(each.getVersion().toString(), winnerNode.getVersion().toString()))
				{
					state = MavenArtifactState.CONFLICT;
				}
				else
				{
					state = MavenArtifactState.DUPLICATE;
				}
			}

			ma.setScope(scope);
			MavenArtifactNode newNode = new MavenArtifactNode(parent, ma, state, relatedArtifact, each.getDependency().getScope(), premanagedVersion, premanagedScope);
			newNode.setDependencies(convertAetherDependencyNodes(newNode, each.getChildren(), nativeToConvertedMap, localRepository));
			result.add(newNode);
		}
		return result;
	}

	@Nullable
	public static Artifact toArtifact(@Nullable Dependency dependency)
	{
		if(dependency == null)
		{
			return null;
		}

		Artifact result = RepositoryUtils.toArtifact(dependency.getArtifact());
		if(result == null)
		{
			return null;
		}
		result.setScope(dependency.getScope());
		result.setOptional(dependency.isOptional());
		return result;
	}
}
