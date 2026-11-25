@echo off
setlocal
cd /d "%~dp0backend"
if not defined JAVA_HOME if exist "C:\Program Files\Java\jdk-21\bin\java.exe" set "JAVA_HOME=C:\Program Files\Java\jdk-21"
call mvn -Dmaven.repo.local=../.tools/m2 spring-boot:run
endlocal
