/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.schema;

import org.gradle.api.tasks.LocalState;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.schema.AbstractPropertySchemaExtractor;

public class LocalStatePropertySchemaExtractor extends AbstractPropertySchemaExtractor<TaskInstanceSchema.Builder> {
    public static final LocalStatePropertySchemaExtractor LOCAL_STATE = new LocalStatePropertySchemaExtractor();

    private LocalStatePropertySchemaExtractor() {
        super(LocalState.class);
    }

    @Override
    public void extractProperty(String qualifiedName, PropertyMetadata metadata, Object parent, TaskInstanceSchema.Builder builder) {
        builder.add(new DefaultLocalStatePropertySchema(qualifiedName, metadata.isAnnotationPresent(Optional.class), () -> metadata.getPropertyValue(parent)));
    }
}
