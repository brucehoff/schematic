import os
import json
import connexion
import logging

from schematic import CONFIG

# TODO This duplicates the logging config in schematic/__init__.py should do it in just one place
logging.basicConfig(
    format=("%(levelname)s: [%(asctime)s] %(name)s" " - %(message)s"),
    level=logging.DEBUG,
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger(__name__)

def create_app():
    connexionapp = connexion.FlaskApp(__name__, specification_dir="openapi/")
    connexionapp.add_api("api.yaml")

    # get the underlying Flask app instance
    app = connexionapp.app

    # path to config.yml file saved as a Flask config variable
    app.config["SCHEMATIC_CONFIG"] = os.path.abspath(
        os.path.join(__file__, "../../config.yml")
    )

    # Configure flask app
    # app.config[] = schematic[]
    # app.config[] = schematic[]
    # app.config[] = schematic[]

    # Initialize extension schematic
    # import MyExtension
    # myext = MyExtension()
    # myext.init_app(app)

     # If AWS secrets manager injected secrets, then convert them to 'normal' env vars
    SECRETS_MANAGER_ENV_NAME = "SECRETS_MANAGER_SECRETS" # TODO This is defined in docker_fargate_stack.py  Need to define in just one place.
    secrets_manager_data = os.getenv(SECRETS_MANAGER_ENV_NAME)
    if secrets_manager_data is not None:
    	logger.info(f"FOUND env var {SECRETS_MANAGER_ENV_NAME}.")
    	json_dict = json.loads(secrets_manager_data)
    	for key, value in json_dict.items():
    		logger.info(f"Set env var for {key}")
    		os.environ[key]=value
    else:
    	logger.info(f"No env var {SECRETS_MANAGER_ENV_NAME} found.")

    return app


# def route_code():
#     import flask_schematic as sc
#     sc.method1()
#
