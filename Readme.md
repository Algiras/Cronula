# Cronula

Streaming timed actions with greyhounds

Kafka setup via docker: [docker-compose.yml](devEnv/kafka-docker/docker-compose.yml)

- Docker compose also provides a kafka ui tool via [http://localhost:8000](http://localhost:8000)

Postman config for trying out: [postman-schema](misc/Cronula.postman_collection.json)

For running the build you will first need to run `docker-compose` in `devEnv/kafka-docker` directory
and set `FP_HOST` and `FP_PORT` to specify on what port to run this service