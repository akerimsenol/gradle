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

package org.gradle.api.tasks.diagnostics;

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Property;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Base class for tasks which reports on attributes of a variant or configuration.
 *
 * @since 7.5
 */
@Incubating
@DisableCachingByDefault(because = "Produces only non-cacheable console output")
public abstract class AbstractVariantsReportTask extends DefaultTask {
    protected abstract Predicate<Configuration> configurationsToReportFilter();

    protected abstract String targetName();
    protected abstract String targetTypeDesc();

    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        throw new UnsupportedOperationException();
    }

    protected List<Configuration> configurationsToReport() {
        return getConfigurations(configurationsToReportFilter());
    }

    protected List<Configuration> getConfigurations(Predicate<Configuration> filter) {
        return getProject().getConfigurations()
            .stream()
            .filter(filter)
            .sorted(Comparator.comparing(Configuration::getName))
            .collect(Collectors.toList());
    }

    protected void reportNoMatch(Property<String> searchTarget, List<Configuration> configurations, StyledTextOutput output) {
        if (searchTarget.isPresent()) {
            output.println("There is no " + targetName() + " named '" + searchTarget.get() + "' defined on this project.");
            configurations = getConfigurations(Configuration::isCanBeConsumed);
        }

        if (configurations.isEmpty()) {
            output.println("There are no " + targetTypeDesc() + " " + targetName() + "s on project " + getProject().getName());
        } else {
            output.println("Here are the available " + targetTypeDesc() + " " + targetName() + "s: " + configurations.stream().map(Configuration::getName).collect(Collectors.joining(", ")));
        }
    }
}