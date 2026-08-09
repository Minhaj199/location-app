@ECHO OFF
SETLOCAL
SET "APP_HOME=%~dp0"
SET "CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"

IF DEFINED JAVA_HOME GOTO findJavaFromJavaHome
SET JAVA_EXE=java.exe
GOTO execute

:findJavaFromJavaHome
SET JAVA_EXE=%JAVA_HOME%\bin\java.exe

:execute
"%JAVA_EXE%" -Dorg.gradle.appname=gradlew -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
