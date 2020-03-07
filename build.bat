@echo off

set DEBUG_MODE=

if "%1" == "debug" (
  set DEBUG_MODE=debug
)

cd net.frontuari.payselection.targetplatform
call .\plugin-builder.bat %DEBUG_MODE% ..\net.frontuari.payselection ..\net.frontuari.payselection.test
cd ..
