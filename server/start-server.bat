@echo off
chcp 65001 >nul
cd /d "%~dp0"

if exist ".env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do set "%%a=%%b"
)

if "%DEEPSEEK_API_KEY%"=="" (
  echo [WARN] 未找到 DEEPSEEK_API_KEY，Agent 接口会返回鉴权失败
  echo        请把 .env.example 复制为 .env 并填入真实 key
) else (
  echo [OK] 已加载 DEEPSEEK_API_KEY，长度 %DEEPSEEK_API_KEY:~0,3%***
)

mvn -DskipTests spring-boot:run
