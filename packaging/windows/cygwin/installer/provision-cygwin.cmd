@echo off
"%~dp0runtime\bin\java.exe" --enable-native-access=ALL-UNNAMED -cp "%~dp0app\cygwin-tool.jar" CygwinTool ensure --seed "%~dp0app\cygwin-seed"
exit /b %errorlevel%
