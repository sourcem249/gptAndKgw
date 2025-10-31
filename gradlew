#!/usr/bin/env sh

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS="-Xmx64m"

APP_BASE_NAME=`basename "$0"`
APP_HOME=`dirname "$0"`

# Resolve any ".." to get the full path
APP_HOME=`cd "$APP_HOME" && pwd`

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ ! -f "$CLASSPATH" ]; then
    echo "Gradle wrapper JAR not found. Please run 'gradle wrapper' to generate it." >&2
    exit 1
fi

exec "${JAVA_HOME}/bin/java" ${DEFAULT_JVM_OPTS} -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
