#!/bin/sh
#
# Gradle wrapper script for Android projects
#
DIRNAME=$(dirname "$0")
cd "$DIRNAME" || exit 1

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# Determine the Java command to use
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Setup the classpath
CLASSPATH="$DIRNAME/gradle/wrapper/gradle-wrapper.jar"

# Execute Gradle
exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
