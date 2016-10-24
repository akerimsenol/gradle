/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.FileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;

import java.util.Map;
import java.util.Set;

public class FileDependencyCollectingGraphVisitor implements DependencyGraphVisitor, FileDependencyResults {
    private final SetMultimap<ResolvedConfigurationIdentifier, FileCollection> filesByConfiguration = LinkedHashMultimap.create();
    private Map<FileCollectionDependency, FileCollection> rootFiles;

    @Override
    public void start(DependencyGraphNode root) {
        Set<LocalFileDependencyMetadata> fileDependencies = ((LocalConfigurationMetadata) root.getMetadata()).getFiles();
        rootFiles = Maps.newLinkedHashMap();
        for (LocalFileDependencyMetadata fileDependency : fileDependencies) {
            rootFiles.put(fileDependency.getSource(), fileDependency.getFiles());
            filesByConfiguration.put(root.getNodeId(), fileDependency.getFiles());
        }
    }

    @Override
    public void visitNode(DependencyGraphNode resolvedConfiguration) {
    }

    @Override
    public void visitEdge(DependencyGraphNode resolvedConfiguration) {
        ConfigurationMetadata configurationMetadata = resolvedConfiguration.getMetadata();
        if (configurationMetadata instanceof LocalConfigurationMetadata) {
            LocalConfigurationMetadata localConfigurationMetadata = (LocalConfigurationMetadata) configurationMetadata;
            for (DependencyGraphEdge edge : resolvedConfiguration.getIncomingEdges()) {
                if (edge.isTransitive()) {
                    Set<LocalFileDependencyMetadata> fileDependencies = localConfigurationMetadata.getFiles();
                    for (LocalFileDependencyMetadata fileDependency : fileDependencies) {
                        filesByConfiguration.put(resolvedConfiguration.getNodeId(), fileDependency.getFiles());
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void finish(DependencyGraphNode root) {
    }

    @Override
    public Map<FileCollectionDependency, FileCollection> getFirstLevelFiles() {
        return rootFiles;
    }

    @Override
    public Set<FileCollection> getFiles(ResolvedConfigurationIdentifier node) {
        return filesByConfiguration.get(node);
    }

    @Override
    public Set<FileCollection> getFiles() {
        return ImmutableSet.copyOf(filesByConfiguration.values());
    }
}
