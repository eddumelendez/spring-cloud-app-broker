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

package org.springframework.cloud.appbroker.workflow.instance;

import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;

import org.springframework.cloud.appbroker.deployer.BackingAppDeploymentService;
import org.springframework.cloud.appbroker.deployer.BackingServicesProvisionService;
import org.springframework.cloud.appbroker.deployer.BrokeredServices;
import org.springframework.cloud.appbroker.extensions.credentials.CredentialProviderService;
import org.springframework.cloud.appbroker.extensions.targets.TargetService;
import org.springframework.cloud.appbroker.service.DeleteServiceInstanceWorkflow;
import org.springframework.cloud.servicebroker.model.instance.DeleteServiceInstanceRequest;
import org.springframework.cloud.servicebroker.model.instance.DeleteServiceInstanceResponse;
import org.springframework.cloud.servicebroker.model.instance.DeleteServiceInstanceResponse.DeleteServiceInstanceResponseBuilder;
import org.springframework.core.annotation.Order;

@Order(0)
public class AppDeploymentDeleteServiceInstanceWorkflow
	extends AppDeploymentInstanceWorkflow
	implements DeleteServiceInstanceWorkflow {

	private final Logger log = Loggers.getLogger(AppDeploymentDeleteServiceInstanceWorkflow.class);

	private final BackingAppDeploymentService deploymentService;
	private final CredentialProviderService credentialProviderService;
	private final TargetService targetService;
	private final BackingServicesProvisionService backingServicesProvisionService;

	public AppDeploymentDeleteServiceInstanceWorkflow(BrokeredServices brokeredServices,
													  BackingAppDeploymentService deploymentService,
													  BackingServicesProvisionService backingServicesProvisionService,
													  CredentialProviderService credentialProviderService,
													  TargetService targetService) {
		super(brokeredServices);
		this.deploymentService = deploymentService;
		this.credentialProviderService = credentialProviderService;
		this.targetService = targetService;
		this.backingServicesProvisionService = backingServicesProvisionService;
	}

	@Override
	public Mono<Void> delete(DeleteServiceInstanceRequest request, DeleteServiceInstanceResponse response) {
		return
			getBackingServicesForService(request.getServiceDefinition(), request.getPlan())
				.flatMapMany(backingService ->
					targetService.addToBackingServices(backingService,
						getTargetForService(request.getServiceDefinition(), request.getPlan()) ,
						request.getServiceInstanceId()))
				.flatMap(backingServicesProvisionService::deleteServiceInstance)
				.thenMany(
					getBackingApplicationsForService(request.getServiceDefinition(), request.getPlan())
						.flatMap(backingApplications ->
							credentialProviderService.deleteCredentials(backingApplications,
								request.getServiceInstanceId()))
						.flatMap(backingApps ->
							targetService.addToBackingApplications(backingApps,
								getTargetForService(request.getServiceDefinition(), request.getPlan()),
								request.getServiceInstanceId()))
						.flatMapMany(deploymentService::undeploy)
						.doOnRequest(l -> log.debug("Undeploying applications {}", brokeredServices))
						.doOnEach(result -> log.debug("Finished undeploying {}", result))
						.doOnComplete(() -> log.debug("Finished undeploying applications {}", brokeredServices))
						.doOnError(exception -> log.error("Error undeploying applications {} with error '{}'",
							brokeredServices, exception.getMessage())))
				.then();
	}

	@Override
	public Mono<Boolean> accept(DeleteServiceInstanceRequest request) {
		return accept(request.getServiceDefinition(), request.getPlan());
	}

	@Override
	public Mono<DeleteServiceInstanceResponseBuilder> buildResponse(DeleteServiceInstanceRequest request,
																	DeleteServiceInstanceResponseBuilder responseBuilder) {
		return Mono.just(responseBuilder.async(true));
	}
}
