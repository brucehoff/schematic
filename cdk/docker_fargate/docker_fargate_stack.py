
from aws_cdk import (Stack,
	aws_ec2 as ec2, aws_ecs as ecs,
	aws_ecs_patterns as ecs_patterns,
	aws_ssm as ssm,
	Tags)

from constructs import Construct
import os
import aws_cdk.aws_secretsmanager as sm

STACK_NAME_PREFIX = "STACK_NAME_PREFIX"
ID_SUFFIX = "-DockerFargateStack"
VPC_SUFFIX = "-FargateVPC"
CLUSTER_SUFFIX = "-Cluster"
SERVICE_SUFFIX = "-Service"
DOCKER_IMAGE_NAME = "DOCKER_IMAGE"
COST_CENTER = "COST_CENTER"
COST_CENTER_TAG_NAME = "CostCenter"
PORT_NUMBER = "PORT"
SECRETS_MANAGER_SYNAPSE_AUTH_TOKEN_KEY = "SECRETS_MANAGER_SYNAPSE_AUTH_TOKEN_KEY"
SECRETS_MANAGER_GOOGLE_AUTH_JSON_KEY = "SECRETS_MANAGER_GOOGLE_AUTH_JSON_KEY"
SECRETS_MANAGER_ENV_PREFIX = "secrets-manager-"

def get_required_env(name: str) -> str:
        value = os.getenv(name)
        if value is None or len(value)==0:
            raise Exception(f'{name} is required.')
        return value
		
def create_id() -> str:
        return get_required_env(STACK_NAME_PREFIX)+ID_SUFFIX

# the following are the secret names as they will appear inside the container
SECRET_ENV_NAME_SYNAPSE_AUTH_TOKEN = \
  SECRETS_MANAGER_ENV_PREFIX+SECRETS_MANAGER_SYNAPSE_AUTH_TOKEN_KEY
SECRET_ENV_NAME_GOOGLE_AUTH_JSON = \
  SECRETS_MANAGER_ENV_PREFIX+SECRETS_MANAGER_GOOGLE_AUTH_JSON_KEY

CONTAINER_ENV = "CONTAINER_ENV" # name of env passed from GitHub action
ENV_NAME = "ENV" # name of env as seen in container

def get_vpc_name() -> str:
    return get_required_env(STACK_NAME_PREFIX)+VPC_SUFFIX

def get_cluster_name() -> str:
    return get_required_env(STACK_NAME_PREFIX)+CLUSTER_SUFFIX

def get_service_name() -> str:
    return get_required_env(STACK_NAME_PREFIX)+SERVICE_SUFFIX

def get_docker_image_name():
    return get_required_env(DOCKER_IMAGE_NAME)

def get_cost_center() -> str:
    return get_required_env(COST_CENTER)

def get_port() -> int:
    return int(get_required_env(PORT_NUMBER))

def get_secrets_manager_synapse_auth_token_key() -> str:
    return get_required_env(SECRETS_MANAGER_SYNAPSE_AUTH_TOKEN_KEY)

def get_secrets_manager_google_auth_json_key() -> str:
    return get_required_env(SECRETS_MANAGER_GOOGLE_AUTH_JSON_KEY)

def get_container_env() -> str:
    return os.getenv(CONTAINER_ENV)
          
def create_secret(scope: Construct, name: str) -> str:
    isecret = sm.Secret.from_secret_name_v2(scope, name, name)
    return ecs.Secret.from_secrets_manager(isecret)
    # see also: https://docs.aws.amazon.com/cdk/api/v1/python/aws_cdk.aws_ecs/Secret.html
    # see also: ecs.Secret.from_ssm_parameter(ssm.IParameter(parameter_name=name))

class DockerFargateStack(Stack):

    def __init__(self, scope: Construct, **kwargs) -> None:
    	stack_id = create_id()
    	super().__init__(scope, stack_id, **kwargs)

    	vpc = ec2.Vpc(self, get_vpc_name(), max_azs=2)

    	cluster = ecs.Cluster(self, get_cluster_name(), vpc=vpc)
        
    	secrets = {
        	SECRET_ENV_NAME_SYNAPSE_AUTH_TOKEN: create_secret(self, get_secrets_manager_synapse_auth_token_key()),
        	SECRET_ENV_NAME_GOOGLE_AUTH_JSON:  create_secret(self, get_secrets_manager_google_auth_json_key())
        }
        
    	env_vars = {}
    	container_env = get_container_env()
    	if container_env is not None:
    	    env_vars[ENV_NAME]=container_env
        
    	task_image_options = ecs_patterns.ApplicationLoadBalancedTaskImageOptions(
    	    	image=ecs.ContainerImage.from_registry(get_docker_image_name()),
    	    	environment=env_vars,
    	    	secrets = secrets,
    	    	container_port = get_port())

		#
		# for options to pass to ApplicationLoadBalancedTaskImageOptions see:
		# https://docs.aws.amazon.com/cdk/api/v1/python/aws_cdk.aws_ecs_patterns/ApplicationLoadBalancedTaskImageOptions.html#aws_cdk.aws_ecs_patterns.ApplicationLoadBalancedTaskImageOptions
		#
    	albfs = ecs_patterns.ApplicationLoadBalancedFargateService(self, get_service_name(),
            cluster=cluster,            # Required
            cpu=256,                    # Default is 256
            desired_count=2,            # Default is 1
            task_image_options=task_image_options,
            memory_limit_mib=1024,      # Default is 512
            public_load_balancer=True)  # Default is False

    	Tags.of(albfs).add(COST_CENTER_TAG_NAME, get_cost_center())
