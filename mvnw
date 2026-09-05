#!/bin/sh
set -e

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_JAR="$BASE_DIR/.mvn/wrapper/maven-wrapper.jar"
MAVEN_USER_HOME="${MAVEN_USER_HOME:-$HOME/.m2}"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Maven Wrapper jar is missing at $WRAPPER_JAR." >&2
  echo "Install Maven or generate the wrapper with: mvn -N wrapper:wrapper -Dmaven=3.9.11" >&2
  exit 1
fi

exec java -Duser.home="$HOME" -Dmaven.user.home="$MAVEN_USER_HOME" -Dmaven.multiModuleProjectDirectory="$BASE_DIR" -classpath "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"
