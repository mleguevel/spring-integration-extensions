/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.dsl.kotlin

import org.springframework.integration.dsl.GenericEndpointSpec
import org.springframework.integration.dsl.IntegrationFlowDefinition
import org.springframework.integration.transformer.MessageTransformingHandler

/**
 * Extension for [IntegrationFlowDefinition.convert] providing a `convert<MyType>()` variant.
 *
 * @author Artem Bilan
 */
inline fun <reified T> IntegrationFlowDefinition<*>.convert(
		crossinline configurer: (GenericEndpointSpec<MessageTransformingHandler>) -> Unit = {}):
		IntegrationFlowDefinition<*> =
		convert(T::class.java) { configurer(it) }

/**
 * Extension for [IntegrationFlowDefinition.transform] providing a `transform<MyTypeIn, MyTypeOut>()` variant.
 *
 * @author Artem Bilan
 */
inline fun <reified P, T> IntegrationFlowDefinition<*>.reifiedTransform(
		crossinline function: (P) -> T,
		crossinline configurer: (GenericEndpointSpec<MessageTransformingHandler>) -> Unit = {}):
		IntegrationFlowDefinition<*> =
		transform(P::class.java, { function(it) }) { configurer(it) }
