/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.GradleException
import org.gradle.api.internal.BuildType
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.internal.tasks.TaskExecutionAccessChecker
import org.gradle.configurationcache.initialization.ConfigurationCacheInjectedClasspathInstrumentationStrategy
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.configurationcache.initialization.VintageInjectedClasspathInstrumentationStrategy
import org.gradle.configurationcache.problems.ConfigurationCacheProblems
import org.gradle.configurationcache.serialization.beans.BeanStateReaderLookup
import org.gradle.configurationcache.serialization.beans.BeanStateWriterLookup
import org.gradle.configurationcache.serialization.codecs.jos.JavaSerializationEncodingLookup
import org.gradle.configurationcache.services.EnvironmentChangeTracker
import org.gradle.execution.selection.BuildTaskSelector
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildoption.DefaultInternalOptions
import org.gradle.internal.buildoption.InternalFlag
import org.gradle.internal.buildtree.BuildActionModelRequirements
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.buildtree.BuildTreeModelControllerServices
import org.gradle.internal.buildtree.BuildTreeWorkGraphPreparer
import org.gradle.internal.buildtree.DefaultBuildTreeWorkGraphPreparer
import org.gradle.internal.buildtree.RunTasksRequirements
import org.gradle.internal.service.ServiceRegistration
import org.gradle.util.internal.IncubationLogger


class DefaultBuildTreeModelControllerServices : BuildTreeModelControllerServices {
    companion object {
        private
        val parallelBuilding = InternalFlag("org.gradle.internal.tooling.parallel", true)

        private
        val invalidateCoupledProjects = InternalFlag("org.gradle.internal.invalidate-coupled-projects", true)
    }

    override fun servicesForBuildTree(requirements: BuildActionModelRequirements): BuildTreeModelControllerServices.Supplier {
        val startParameter = requirements.startParameter

        // Isolated projects also implies configuration cache
        if (startParameter.isolatedProjects.get() && !startParameter.configurationCache.get()) {
            if (startParameter.configurationCache.isExplicit) {
                throw GradleException("The configuration cache cannot be disabled when isolated projects is enabled.")
            }
        }

        val options = DefaultInternalOptions(startParameter.systemPropertiesArgs)
        val isolatedProjects = startParameter.isolatedProjects.get()
        val parallelToolingActions = (isolatedProjects || requirements.startParameter.isParallelProjectExecutionEnabled) && options.getOption(parallelBuilding).get()
        val invalidateCoupledProjects = isolatedProjects && options.getOption(invalidateCoupledProjects).get()
        val modelParameters = if (requirements.isCreatesModel) {
            // When creating a model, disable certain features - only enable configure on demand and configuration cache when isolated projects is enabled
            BuildModelParameters(isolatedProjects, isolatedProjects, isolatedProjects, true, isolatedProjects, parallelToolingActions, invalidateCoupledProjects)
        } else {
            val configurationCache = startParameter.configurationCache.get() || isolatedProjects
            val configureOnDemand = startParameter.isConfigureOnDemand || isolatedProjects
            BuildModelParameters(configureOnDemand, configurationCache, isolatedProjects, false, false, parallelToolingActions, invalidateCoupledProjects)
        }

        if (!startParameter.isConfigurationCacheQuiet) {
            if (modelParameters.isIsolatedProjects) {
                IncubationLogger.incubatingFeatureUsed("Isolated projects")
            } else if (modelParameters.isConfigurationCache) {
                IncubationLogger.incubatingFeatureUsed("Configuration cache")
            }
        }
        if (!modelParameters.isIsolatedProjects && modelParameters.isConfigureOnDemand) {
            IncubationLogger.incubatingFeatureUsed("Configuration on demand")
        }

        return BuildTreeModelControllerServices.Supplier { registration ->
            val buildType = if (requirements.isRunsTasks) BuildType.TASKS else BuildType.MODEL
            registration.add(BuildType::class.java, buildType)
            registerServices(registration, modelParameters, requirements)
        }
    }

    override fun servicesForNestedBuildTree(startParameter: StartParameterInternal): BuildTreeModelControllerServices.Supplier {
        return BuildTreeModelControllerServices.Supplier { registration ->
            registration.add(BuildType::class.java, BuildType.TASKS)
            // Configuration cache is not supported for nested build trees
            val buildModelParameters = BuildModelParameters(startParameter.isConfigureOnDemand, false, false, true, false, false, false)
            val requirements = RunTasksRequirements(startParameter)
            registerServices(registration, buildModelParameters, requirements)
        }
    }

    private
    fun registerServices(registration: ServiceRegistration, modelParameters: BuildModelParameters, requirements: BuildActionModelRequirements) {
        registration.add(BuildModelParameters::class.java, modelParameters)
        registration.add(BuildActionModelRequirements::class.java, requirements)
        if (modelParameters.isConfigurationCache) {
            registration.add(EnvironmentChangeTracker::class.java)
            registration.add(ConfigurationCacheBuildTreeLifecycleControllerFactory::class.java)
            registration.add(ConfigurationCacheStartParameter::class.java)
            registration.add(ConfigurationCacheClassLoaderScopeRegistryListener::class.java)
            registration.add(ConfigurationCacheInjectedClasspathInstrumentationStrategy::class.java)
            registration.add(ConfigurationCacheProblems::class.java)
            registration.add(DefaultConfigurationCache::class.java)
            registration.add(BeanStateWriterLookup::class.java)
            registration.add(BeanStateReaderLookup::class.java)
            registration.add(JavaSerializationEncodingLookup::class.java)
            registration.addProvider(ConfigurationCacheBuildTreeProvider())
        } else {
            registration.add(VintageInjectedClasspathInstrumentationStrategy::class.java)
            registration.add(VintageBuildTreeLifecycleControllerFactory::class.java)
            registration.addProvider(VintageBuildTreeProvider())
        }
        registration.addProvider(TaskExecutionAccessCheckerProvider(modelParameters.isConfigurationCache))
    }

    private
    class ConfigurationCacheBuildTreeProvider {
        fun createBuildTreeWorkGraphPreparer(buildRegistry: BuildStateRegistry, buildTaskSelector: BuildTaskSelector, cache: BuildTreeConfigurationCache): BuildTreeWorkGraphPreparer {
            return ConfigurationCacheAwareBuildTreeWorkGraphPreparer(DefaultBuildTreeWorkGraphPreparer(buildRegistry, buildTaskSelector), cache)
        }
    }

    private
    class VintageBuildTreeProvider {
        fun createBuildTreeWorkGraphPreparer(buildRegistry: BuildStateRegistry, buildTaskSelector: BuildTaskSelector): BuildTreeWorkGraphPreparer {
            return DefaultBuildTreeWorkGraphPreparer(buildRegistry, buildTaskSelector)
        }
    }

    private
    class TaskExecutionAccessCheckerProvider(
        private val isConfigurationCacheEnabled: Boolean
    ) {
        fun createTaskExecutionAccessChecker(
            configurationTimeBarrier: ConfigurationTimeBarrier,
            /** In non-CC builds, [ConfigurationCacheStartParameter] is not registered; accepting a list here is a way to ignore its absence. */
            configurationCacheStartParameter: List<ConfigurationCacheStartParameter>
        ): TaskExecutionAccessChecker = when {
            !isConfigurationCacheEnabled -> TaskExecutionAccessCheckers.TaskStateBased
            configurationCacheStartParameter.single().taskExecutionAccessPreStable -> TaskExecutionAccessCheckers.TaskStateBased
            else -> TaskExecutionAccessCheckers.ConfigurationTimeBarrierBased(configurationTimeBarrier)
        }
    }
}
