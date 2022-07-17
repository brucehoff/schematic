package com.myorg;
import java.util.Collections;
import java.util.Map;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Tags;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.Secret;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.constructs.Construct;
import org.apache.commons.lang.StringUtils;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import java.util.Map;
import java.util.HashMap;

public class DockerFargateStack extends Stack {
	private static final String STACK_NAME_PREFIX = "STACK_NAME_PREFIX";
	private static final String ID_SUFFIX = "-DockerFargateStack";
	private static final String VPC_SUFFIX = "-FargateVPC";
	private static final String CLUSTER_SUFFIX = "-Cluster";
	private static final String SERVICE_SUFFIX = "-Service";
	private static final String DOCKER_IMAGE_NAME = "DOCKER_IMAGE";
	private static final String COST_CENTER = "COST_CENTER";
	private static final String COST_CENTER_TAG_NAME = "CostCenter";
	private static final String PORT_NUMBER = "PORT";
	private static final String SECRETS_MANAGER_SYNAPSE_AUTH_TOKEN_KEY = "synapse-auth-token";
	private static final String SECRETS_MANAGER_GOOGLE_AUTH_JSON_KEY = "google-auth-json";
	private static final String SECRETS_MANAGER_ENV_PREFIX = "secrets-manager-";
	
	// the following are the secret names as they will appear inside the container
	private static final String SECRET_ENV_NAME_SYNAPSE_AUTH_TOKEN =
			SECRETS_MANAGER_ENV_PREFIX+SECRETS_MANAGER_SYNAPSE_AUTH_TOKEN_KEY;
	private static final String SECRET_ENV_NAME_GOOGLE_AUTH_JSON =
			SECRETS_MANAGER_ENV_PREFIX+SECRETS_MANAGER_GOOGLE_AUTH_JSON_KEY;
	
	private static final String CONTAINER_ENV = "CONTAINER_ENV"; // name of env passed from GitHub action
	private static final String ENV_NAME = "ENV"; // name of env as seen in container
	
	private String id;
	
	private static String getRequiredEnv(String envName) {
		String result = System.getenv(envName);
		if (StringUtils.isEmpty(result)) {
			throw new IllegalStateException(envName+" is required.");
		}
		return result;
	}
	
	private static String getId() {
		return getRequiredEnv(STACK_NAME_PREFIX)+ID_SUFFIX;
	}
	
	private static String getVpcName() {
		return getRequiredEnv(STACK_NAME_PREFIX)+VPC_SUFFIX;
	}
	
	private static String getClusterName() {
		return getRequiredEnv(STACK_NAME_PREFIX)+CLUSTER_SUFFIX;
	}
	
	private static String getServiceName() {
		return getRequiredEnv(STACK_NAME_PREFIX)+SERVICE_SUFFIX;
	}
	
	private static String getDockerImageName() {
		return getRequiredEnv(DOCKER_IMAGE_NAME);
	}
	
	private static String getCostCenter() {
		return getRequiredEnv(COST_CENTER);
	}
	
	private static int getPort() {
		return Integer.parseInt(getRequiredEnv(PORT_NUMBER));
	}
	
	private static String getSecretsManagerSynapseAuthTokenKey() {
		return getRequiredEnv(STACK_NAME_PREFIX)+"/"+
				System.getenv(SECRETS_MANAGER_SYNAPSE_AUTH_TOKEN_KEY);
	}
	
	private static String getSecretsManagerGoogleAuthJSONKey() {
		return getRequiredEnv(STACK_NAME_PREFIX)+"/"+
				System.getenv(SECRETS_MANAGER_GOOGLE_AUTH_JSON_KEY);
	}
	
	private static String getContainerEnv() {
		return System.getenv(CONTAINER_ENV);
	}
	
	public DockerFargateStack(final Construct scope) {
		this(scope, null);
	}
	
	private Secret createSecret(String name) {
		ISecret isecret = software.amazon.awscdk.services.secretsmanager.
				Secret.fromSecretNameV2(this, id, name);
		return Secret.fromSecretsManager(isecret);
	}


	public DockerFargateStack(final Construct scope, final StackProps props) {
		super(scope, getId(), props);
		this.id=getId();

		Vpc vpc = Vpc.Builder.create(this, getVpcName())
				.maxAzs(2)
				.build();

		Cluster cluster = Cluster.Builder.create(this, getClusterName())
				.vpc(vpc).build();
		
		ApplicationLoadBalancedTaskImageOptions.Builder imageOptionsBuilder = 
				ApplicationLoadBalancedTaskImageOptions.builder()
				.image(ContainerImage.fromRegistry(getDockerImageName()))
				.containerPort(getPort());
		
		Map<String, Secret> secrets = 
				new HashMap<>();
		secrets.put(SECRET_ENV_NAME_SYNAPSE_AUTH_TOKEN, 
				createSecret(getSecretsManagerSynapseAuthTokenKey()));
		secrets.put(SECRET_ENV_NAME_GOOGLE_AUTH_JSON, 
				createSecret(getSecretsManagerGoogleAuthJSONKey()));
		imageOptionsBuilder.secrets(secrets);
		
		// optionally pass in a plain-text (not secret) environment variable
		String containerEnvVar = getContainerEnv();
		if (StringUtils.isNotEmpty(containerEnvVar)) {
			Map<String,String> envVars = Collections.singletonMap(ENV_NAME, containerEnvVar);
			imageOptionsBuilder.environment(envVars);
		}
		ApplicationLoadBalancedTaskImageOptions imageOptions = 
				imageOptionsBuilder.build();

		// Create a load-balanced Fargate service and make it public
		ApplicationLoadBalancedFargateService albfs = 
				ApplicationLoadBalancedFargateService.Builder.create(this, getServiceName())
				.cluster(cluster)           // Required
				.cpu(256)                   // Default is 256
				.desiredCount(2)            // Default is 1
				.taskImageOptions(imageOptions)
				.memoryLimitMiB(1024)       // Default is 512
				.publicLoadBalancer(true)
				.build();   // Default is false
		
		Tags.of(albfs).add(COST_CENTER_TAG_NAME, getCostCenter());
	}
}
