@rem Gradle startup script for Windows
@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem  Gradle startup script for Windows
@rem ##########################################################################

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

set DEFAULT_JVM_OPTS=-Dfile.encoding=UTF-8 "-Xmx64m" "-Xms64m"

if defined JAVA_HOME (
    set JAVA_EXE=%JAVA_HOME%\bin\java.exe
    if not exist "%JAVA_EXE%" (
        echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
        exit /b 1
    )
) else (
    set JAVA_EXE=java.exe
    java.exe -version >NUL 2>&1
    if errorlevel 1 (
        echo ERROR: JAVA is not found on PATH and JAVA_HOME is not set.
        echo Install Temurin JDK 21 and try again.
        exit /b 1
    )
)

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

endlocal
exit /b %ERRORLEVEL%
