@ECHO OFF
SETLOCAL

IF NOT ""=="%JAVA_HOME%" (
  SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) ELSE (
  SET "JAVA_EXE=java.exe"
)

IF EXIST "%JAVA_EXE%" (
  "%JAVA_EXE%" -classpath "%~dp0\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
) ELSE (
  ECHO.
  ECHO ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
  ECHO.
  ECHO Please set the JAVA_HOME variable in your environment to match the
  ECHO location of your Java installation.
)

ENDLOCAL
