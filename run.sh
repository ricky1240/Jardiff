#!/bin/bash
JAR_FILE="target/jar-compare-util-1.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "$JAR_FILE not found. Building the project..."
    mvn clean install
fi

java -jar $JAR_FILE
