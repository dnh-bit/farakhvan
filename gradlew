#!/bin/sh
#
# Minimal Gradle wrapper launcher (POSIX sh).
# The binary gradle/wrapper/gradle-wrapper.jar is NOT stored in this source drop
# (binary files cannot be produced as text). Generate it once with:
#     gradle wrapper --gradle-version 8.9
# The CI workflow does this automatically when the jar is missing.

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "gradle-wrapper.jar is missing. Run: gradle wrapper --gradle-version 8.9" >&2
    exit 1
fi

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

exec "$JAVACMD" -Xmx64m -Xms64m -Dfile.encoding=UTF-8 \
    -classpath "$WRAPPER_JAR" \
    org.gradle.wrapper.GradleWrapperMain "$@"
