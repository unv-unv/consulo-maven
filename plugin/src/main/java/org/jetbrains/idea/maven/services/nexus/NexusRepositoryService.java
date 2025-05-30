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
package org.jetbrains.idea.maven.services.nexus;

import consulo.maven.rt.server.common.model.MavenArtifactInfo;
import consulo.maven.rt.server.common.model.MavenRepositoryInfo;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.UnmarshalException;
import org.jetbrains.idea.maven.services.MavenRepositoryService;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Gregory.Shrago
 */
public class NexusRepositoryService extends MavenRepositoryService {
    public static MavenRepositoryInfo convertRepositoryInfo(RepositoryType repo) {
        return new MavenRepositoryInfo(repo.getId(), repo.getName(), repo.getContentResourceURI());
    }

    public static MavenArtifactInfo convertArtifactInfo(ArtifactType t) {
        return new MavenArtifactInfo(
            t.getGroupId(),
            t.getArtifactId(),
            t.getVersion(),
            t.getPackaging(),
            t.getClassifier(),
            null,
            t.getRepoId()
        );
    }

    @Nonnull
    @Override
    public String getDisplayName() {
        return "Nexus";
    }

    @Nonnull
    @Override
    public List<MavenRepositoryInfo> getRepositories(@Nonnull String url) throws IOException {
        try {
            List<RepositoryType> repos = new Endpoint.Repositories(url).getRepolistAsRepositories().getData().getRepositoriesItem();
            List<MavenRepositoryInfo> result = new ArrayList<>(repos.size());
            for (RepositoryType repo : repos) {
                if (!"maven2".equals(repo.getProvider())) {
                    continue;
                }
                result.add(convertRepositoryInfo(repo));
            }
            return result;
        }
        catch (UnmarshalException e) {
            return Collections.emptyList();
        }
        catch (JAXBException e) {
            throw new IOException(e);
        }
    }

    @Nonnull
    @Override
    public List<MavenArtifactInfo> findArtifacts(@Nonnull String url, @Nonnull MavenArtifactInfo template) throws IOException {
        try {
            final String packaging = StringUtil.notNullize(template.getPackaging());
            String name = Stream.of(template.getGroupId(), template.getArtifactId(), template.getVersion())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(":"));
            final SearchResults results = new Endpoint.DataIndex(url).getArtifactlistAsSearchResults(
                name,
                template.getGroupId(),
                template.getArtifactId(),
                template.getVersion(),
                null,
                template.getClassNames()
            );
            boolean tooManyResults = results.isTooManyResults();
            final SearchResults.Data data = results.getData();
            final ArrayList<MavenArtifactInfo> result = new ArrayList<>();
            if (data != null) {
                for (ArtifactType each : data.getArtifact()) {
                    if (!Comparing.equal(each.packaging, packaging)) {
                        continue;
                    }
                    result.add(convertArtifactInfo(each));
                }
            }
            if (tooManyResults) {
                result.add(null);
            }
            return result;
        }
        catch (UnmarshalException e) {
            return Collections.emptyList();
        }
        catch (JAXBException e) {
            throw new IOException(e);
        }
    }
}
