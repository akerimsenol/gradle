/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A serializer used for resolution results that will be consumed from the same Gradle invocation that produces them.
 *
 * <p>Writes a reference to the {@link ComponentGraphResolveState} instance to build the result from, rather than persisting the associated data.</p>
 */
public class ThisBuildOnlyComponentDetailsSerializer implements ComponentDetailsSerializer {
    private final ConcurrentMap<Long, ComponentGraphResolveState> components = new ConcurrentHashMap<>();

    @Override
    public void writeComponentDetails(ComponentGraphResolveState component, Encoder encoder) throws IOException {
        long instanceId = component.getInstanceId();
        components.putIfAbsent(instanceId, component);
        encoder.writeSmallLong(instanceId);
    }

    @Override
    public void readComponentDetails(Decoder decoder, ResolvedComponentVisitor visitor) throws IOException {
        long instanceId = decoder.readSmallLong();
        ComponentGraphResolveState component = components.get(instanceId);
        if (component == null) {
            throw new IllegalStateException("No component with id " + instanceId + " found.");
        }
        visitor.visitComponentDetails(component.getId(), component.getMetadata().getModuleVersionId(), component.getRepositoryId());
    }
}
