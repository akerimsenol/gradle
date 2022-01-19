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

package org.gradle.api.tasks.diagnostics.internal.configurations;

import org.gradle.api.GradleException;
import org.gradle.internal.logging.text.AbstractStyledTextOutput;

import java.io.IOException;
import java.io.Writer;

public final class StylelessTextStyledTextOutput extends AbstractStyledTextOutput {
    private final Writer writer;

    public StylelessTextStyledTextOutput(Writer writer) {
        this.writer = writer;
    }

    @Override
    protected void doAppend(String text) {
        try {
            writer.append(text);
        } catch (IOException e) {
            throw new GradleException("Failed to append text '" + text + "' to output", e);
        }
    }
}