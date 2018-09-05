/*
 * Copyright 2016-2018 the original author or authors.
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

package org.springframework.cloud.appbroker.extensions.parameters;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.appbroker.deployer.BackingApplication;
import org.springframework.cloud.appbroker.deployer.BackingApplications;
import org.springframework.cloud.appbroker.deployer.ParametersTransformerSpec;
import org.springframework.cloud.servicebroker.exception.ServiceBrokerException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ParametersTransformationServiceTest {
	@Test
	void transformParametersWithNoBackingApps() {
		ParametersTransformationService service = new ParametersTransformationService(Collections.emptyList());

		BackingApplications backingApplications = BackingApplications.builder()
			.build();

		StepVerifier
			.create(service.transformParameters(backingApplications, new HashMap<>()))
			.expectNext(backingApplications)
			.verifyComplete();
	}

	@Test
	void transformParametersWithNoTransformers() {
		ParametersTransformationService service = new ParametersTransformationService(Collections.emptyList());

		BackingApplications backingApplications = BackingApplications.builder()
			.backingApplication(BackingApplication.builder().build())
			.build();

		StepVerifier
			.create(service.transformParameters(backingApplications, new HashMap<>()))
			.expectNext(backingApplications)
			.verifyComplete();
	}

	@Test
	void transformParametersWithUnknownTransformer() {
		ParametersTransformationService service = new ParametersTransformationService(Collections.emptyList());

		BackingApplications backingApplications = BackingApplications.builder()
			.backingApplication(BackingApplication.builder()
				.name("misconfigured-app")
				.parameterTransformers(ParametersTransformerSpec.builder()
					.name("uknown-transformer")
					.build())
				.build())
			.build();

		StepVerifier
			.create(service.transformParameters(backingApplications, new HashMap<>()))
			.expectError(ServiceBrokerException.class)
			.verify();
	}

	@Test
	void transformParametersWithTransformers() {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("key1", "value1");
		parameters.put("key2", "value2");

		BackingApplication app1 = BackingApplication.builder()
			.name("app1")
			.parameterTransformers(ParametersTransformerSpec.builder()
				.name("transformer1")
				.build())
			.build();
		BackingApplication app2 = BackingApplication.builder()
			.name("app2")
			.parameterTransformers(ParametersTransformerSpec.builder()
					.name("transformer1")
					.arg("arg1", "value1")
					.arg("arg2", 5)
					.build(),
				ParametersTransformerSpec.builder()
					.name("transformer2")
					.build())
			.build();
		BackingApplications backingApplications = BackingApplications.builder()
			.backingApplication(app1)
			.backingApplication(app2)
			.build();

		TestFactory factory1 = new TestFactory("transformer1");
		TestFactory factory2 = new TestFactory("transformer2");

		ParametersTransformationService service = new ParametersTransformationService(
			Arrays.asList(factory1, factory2));

		StepVerifier
			.create(service.transformParameters(backingApplications, parameters))
			.expectNext(backingApplications)
			.verifyComplete();

		assertThat(factory1.getActualParameters()).isEqualTo(parameters);
		assertThat(factory2.getActualParameters()).isEqualTo(parameters);

		assertThat(factory1.getActualConfig()).isEqualTo(new Config("value1", 5));
		assertThat(factory2.getActualConfig()).isEqualTo(new Config(null, null));
	}

	public static class TestFactory extends ParametersTransformerFactory<Config> {
		private String name;

		private Map<String, Object> actualParameters;
		private Config actualConfig;

		TestFactory(String name) {
			super(Config.class);
			this.name = name;
		}

		@Override
		public String getName() {
			return this.name;
		}

		@Override
		public ParametersTransformer create(Config config) {
			this.actualConfig = config;
			return this::doTransform;
		}

		private Mono<BackingApplication> doTransform(BackingApplication backingApplication,
													 Map<String, Object> parameters) {
			this.actualParameters = parameters;
			return Mono.just(backingApplication);
		}

		Map<String, Object> getActualParameters() {
			return actualParameters;
		}

		public Config getActualConfig() {
			return actualConfig;
		}
	}

	public static class Config {
		public Config() {
		}
		
		public Config(String arg1, Integer arg2) {
			this.arg1 = arg1;
			this.arg2 = arg2;
		}

		private String arg1;
		private Integer arg2;

		public String getArg1() {
			return arg1;
		}

		public void setArg1(String arg1) {
			this.arg1 = arg1;
		}

		public int getArg2() {
			return arg2;
		}

		public void setArg2(int arg2) {
			this.arg2 = arg2;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Config)) return false;
			Config config = (Config) o;
			return Objects.equals(arg2, config.arg2) &&
				Objects.equals(arg1, config.arg1);
		}

		@Override
		public int hashCode() {
			return Objects.hash(arg1, arg2);
		}

		@Override
		public String toString() {
			return "Config{" +
				"arg1='" + arg1 + '\'' +
				", arg2=" + arg2 +
				'}';
		}
	}
}