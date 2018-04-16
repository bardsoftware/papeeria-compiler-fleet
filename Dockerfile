FROM openjdk:8-slim

ARG FLEET_VERSION=compiler-fleet-1.0-SNAPSHOT

ADD ./build/distributions/${FLEET_VERSION}.zip  ./${FLEET_VERSION}.zip

RUN unzip -n ${FLEET_VERSION}.zip

WORKDIR ./${FLEET_VERSION}/bin/