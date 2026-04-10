@echo off
powershell -ExecutionPolicy Bypass -File "%~dp0deploy-with-tunnel.ps1" %*
