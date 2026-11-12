@echo off
rem SpringBook launcher. Keep this file in the same folder as SpringBook.jar.
chcp 65001 >nul
cd /d "%~dp0"

if not exist "SpringBook.jar" goto nojar
where java >nul 2>nul
if not errorlevel 1 goto runjava
if not exist "%JAVA_HOME%\bin\java.exe" goto nojava

"%JAVA_HOME%\bin\java.exe" -jar SpringBook.jar
goto done

:runjava
java -jar SpringBook.jar
goto done

:done
pause
exit /b 0

:nojar
echo.
echo [ERROR] SpringBook.jar not found in this folder.
echo Please keep this file and SpringBook.jar together.
echo.
pause
exit /b 1

:nojava
echo.
echo [ERROR] Java not found.
echo Please install JDK 21 or newer, then run this file again.
echo Download: https://adoptium.net/temurin/releases/
echo.
pause
exit /b 1
