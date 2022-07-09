# Based on https://github.com/Sage-Bionetworks/shiny-module-gallery/blob/feature-47-Docker_file/Dockerfile
# and https://github.com/Sage-Bionetworks/kubernetes-deployments/blob/add-shiny/shiny/example_shiny/Dockerfile
FROM debian:stable
LABEL maintainer="Anthony anthony.williams@sagebase.org"
RUN apt-get update && apt-get install -y --no-install-recommends \
    sudo \
    libcurl4-gnutls-dev \
    libcairo2-dev \
    libxt-dev \
    libssl-dev \
    libssh2-1-dev \
    libxml2-dev \
    git \
    ca-certificates \
    curl \
    && rm -rf /var/lib/apt/lists/*

# add app and code
WORKDIR /home/app

# Edit run_api.py to use Docker host 0.0.0.0 instead of default 127.0.0.1
# THIS IS NOT THE WAY TO DO IT!!!!! RUN git clone https://github.com/Sage-Bionetworks/schematic/
# INSTEAD DO THIS:
COPY schematic/ /home/app/schematic/
WORKDIR /home/app/schematic
RUN echo 'from api import create_app\nif __name__ == "__main__":\n  app = create_app()\n  app.run(host="0.0.0.0", port=3001, debug=True)' > run_api.py

# Set this to avoid poetry creating a virtual env.
ENV POETRY_VIRTUALENVS_CREATE=false

# Install poetry https://python-poetry.org/docs/
RUN apt-get update && apt-get install -y --no-install-recommends python3 pip
RUN curl -sSL https://raw.githubusercontent.com/python-poetry/poetry/master/get-poetry.py | python3 - \
    && $HOME/.poetry/bin/poetry install

RUN addgroup --system app \
    && adduser --system --ingroup app app

RUN chown app:app -R /home/app
USER app
EXPOSE 3001
CMD ["python3", "run_api.py"] # add run_api.py to the python path


