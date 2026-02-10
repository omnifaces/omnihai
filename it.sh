#!/usr/bin/env bash
set -a
source .env.local
set +a

VERBOSE_ARG=""

if [ "$1" = "--verbose" ]; then
    VERBOSE_ARG="-DargLine=-Djava.util.logging.config.file=src/test/resources/logging.properties"
    shift
fi

mvn clean verify -DskipUTs=true -DskipITs=false -Dmaven.javadoc.skip=true $VERBOSE_ARG "$@"
