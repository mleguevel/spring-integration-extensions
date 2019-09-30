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

package org.springframework.integration.dsl.kotlin.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.channel.FluxMessageChannel
import org.springframework.integration.channel.QueueChannel
import org.springframework.integration.config.EnableIntegration
import org.springframework.integration.core.MessagingTemplate
import org.springframework.integration.dsl.Pollers
import org.springframework.integration.dsl.context.IntegrationFlowContext
import org.springframework.integration.dsl.kotlin.convert
import org.springframework.integration.dsl.kotlin.reifiedTransform
import org.springframework.integration.dsl.kotlin.integrationFlow
import org.springframework.integration.endpoint.MessageProcessorMessageSource
import org.springframework.integration.handler.LoggingHandler
import org.springframework.integration.scheduling.PollerMetadata
import org.springframework.integration.support.MessageBuilder
import org.springframework.integration.test.util.OnlyOnceTrigger
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.PollableChannel
import org.springframework.messaging.support.GenericMessage
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.*
import java.util.function.Function

/**
 * @author Artem Bilan
 */
@SpringJUnitConfig
@DirtiesContext
class KotlinDslTests {

	@Autowired
	private lateinit var beanFactory: BeanFactory

	@Autowired
	private lateinit var integrationFlowContext: IntegrationFlowContext

	@Autowired
	private lateinit var convertFlowInput: MessageChannel

	@Test
	fun `convert extension`() {
		assertThat(this.beanFactory.containsBean("kotlinConverter"))

		val replyChannel = QueueChannel()
		val date = Date()
		val testMessage =
				MessageBuilder.withPayload("{\"name\": \"Test\",\"date\": " + date.time + "}")
						.setHeader(MessageHeaders.CONTENT_TYPE, "application/json")
						.setReplyChannel(replyChannel)
						.build()
		this.convertFlowInput.send(testMessage)

		assertThat(replyChannel.receive(10000)?.payload)
				.isNotNull()
				.isInstanceOf(TestPojo::class.java)
				.isEqualTo(TestPojo("Test", date))
	}

	@Autowired
	@Qualifier("functionGateway")
	private lateinit var upperCaseFunction: Function<String, String>

	@Test
	fun `uppercase function`() {
		assertThat(this.upperCaseFunction.apply("test")).isEqualTo("TEST")
	}

	@Autowired
	private lateinit var fromSupplierQueue: PollableChannel

	@Test
	fun `message source flow`() {
		assertThat(this.fromSupplierQueue.receive(10_000)?.payload).isNotNull().isEqualTo("testSource")
	}

	@Autowired
	@Qualifier("functionFlow2.gateway")
	private lateinit var lowerCaseFunction: Function<String, String>

	@Test
	fun `lowercase function`() {
		assertThat(this.lowerCaseFunction.apply("TEST2")).isEqualTo("test2")
	}

	@Autowired
	private lateinit var fixedSubscriberInput: MessageChannel

	@Test
	fun `fixed subscriber channel`() {
		assertThat(MessagingTemplate().convertSendAndReceive(this.fixedSubscriberInput, "test", String::class.java))
				.isEqualTo("test")
	}

	@Autowired
	private lateinit var fromSupplierQueue2: PollableChannel

	@Test
	fun `message source flow2`() {
		assertThat(this.fromSupplierQueue2.receive(10_000)?.payload).isNotNull().isEqualTo("testSource2")
	}

	@Autowired
	private lateinit var testSupplierResult: PollableChannel

	@Test
	fun `supplier flow1`() {
		assertThat(this.testSupplierResult.receive(10_000)?.payload).isNotNull().isEqualTo("testSupplier")
	}

	@Autowired
	private lateinit var testSupplierResult2: PollableChannel

	@Test
	fun `supplier flow2`() {
		assertThat(this.testSupplierResult2.receive(10_000)?.payload).isNotNull().isEqualTo("testSupplier2")
	}

	@Test
	fun `reactive publisher flow`() {
		val fluxChannel = FluxMessageChannel()

		val verifyLater =
				StepVerifier
						.create(Flux.from(fluxChannel).map { it.payload }.cast(Integer::class.java))
						.expectNext(Integer(4), Integer(6))
						.thenCancel()
						.verifyLater()

		val publisher = Flux.just(2, 3).map { GenericMessage(it) }

		val integrationFlow =
				integrationFlow(publisher) {
					it.reifiedTransform<Message<Int>, Int>({ it.payload * 2 }) { it.id("foo") }
							.channel(fluxChannel)
				}

		val registration = this.integrationFlowContext.registration(integrationFlow).register()

		verifyLater.verify()

		registration.destroy()
	}

	@Configuration
	@EnableIntegration
	class Config {

		@Bean(PollerMetadata.DEFAULT_POLLER)
		fun defaultPoller() =
				Pollers.fixedDelay(100).maxMessagesPerPoll(1).get()

		@Bean
		fun convertFlow() =
				integrationFlow("convertFlowInput") {
					it.convert<TestPojo>()
							.convert<TestPojo> { it.id("kotlinConverter") }
				}

		@Bean
		fun functionFlow() =
				integrationFlow<Function<String, String>>({ it.beanName("functionGateway") }) {
					it.transform<String, String> { it.toUpperCase() }
				}

		@Bean
		fun functionFlow2() =
				integrationFlow<Function<*, *>> {
					it.transform<String, String> { it.toLowerCase() }
				}

		@Bean
		fun messageSourceFlow() =
				integrationFlow(MessageProcessorMessageSource { "testSource" },
						{ it.poller { it.trigger(OnlyOnceTrigger()) } }) {
					it.channel { c -> c.queue("fromSupplierQueue") }
				}

		@Bean
		fun messageSourceFlow2() =
				integrationFlow(MessageProcessorMessageSource { "testSource2" }) {
					it.channel { c -> c.queue("fromSupplierQueue2") }
				}

		@Bean
		fun fixedSubscriberFlow() =
				integrationFlow("fixedSubscriberInput", true) {
					it.logAndReply(LoggingHandler.Level.WARN)
				}

		@Bean
		fun flowFromSupplier() =
				integrationFlow({ "testSupplier" }) {
					it.channel { c -> c.queue("testSupplierResult") }
				}

		@Bean
		fun flowFromSupplier2() =
				integrationFlow({ "testSupplier2" },
						{ it.poller { it.trigger(OnlyOnceTrigger()) } }) {
					it.channel { c -> c.queue("testSupplierResult2") }
				}

	}

	data class TestPojo(val name: String?, val date: Date?)

}
