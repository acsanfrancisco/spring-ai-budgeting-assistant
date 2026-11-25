@echo off
setlocal
cd /d "%~dp0" || exit /b 1
set "GIT_BASH=%ProgramFiles%\Git\bin\bash.exe"
if not exist "%GIT_BASH%" set "GIT_BASH=%LocalAppData%\Programs\Git\bin\bash.exe"
if not exist "%GIT_BASH%" (
    for /f "delims=" %%G in ('where git.exe 2^>nul') do if exist "%%~dpG..\bin\bash.exe" set "GIT_BASH=%%~dpG..\bin\bash.exe"
)
if not exist "%GIT_BASH%" (
    echo Instale o Git for Windows com Git Bash e tente novamente.
    pause
    exit /b 1
)
"%GIT_BASH%" ./scripts/iniciar.sh
set "RESULT=%ERRORLEVEL%"
echo.
rem Keep the window open on success too, so the Swagger URL stays readable after a double-click.
pause
exit /b %RESULT%
