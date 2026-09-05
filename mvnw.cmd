@echo off
setlocal
set "BASE_DIR=%~dp0"
if "%BASE_DIR:~-1%"=="\" set "BASE_DIR=%BASE_DIR:~0,-1%"
set "WRAPPER_JAR=%BASE_DIR%\.mvn\wrapper\maven-wrapper.jar"
if "%MAVEN_USER_HOME%"=="" set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
if "%JAVA_USER_HOME%"=="" set "JAVA_USER_HOME=%USERPROFILE%"

if not exist "%WRAPPER_JAR%" (
  echo Maven Wrapper jar is missing at %WRAPPER_JAR%. 1>&2
  echo Install Maven or generate the wrapper with: mvn -N wrapper:wrapper -Dmaven=3.9.11 1>&2
  exit /b 1
)

java -Duser.home="%JAVA_USER_HOME%" -Dmaven.user.home="%MAVEN_USER_HOME%" -Dmaven.multiModuleProjectDirectory="%BASE_DIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
