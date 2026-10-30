@echo off
chcp 65001 >nul
set "ROOT=%~dp0"

echo ========================================
echo   智觅生活 · 一键启动
echo ========================================
echo.

echo [1/3] 启动 Redis (6380) ...
start "Redis 6380" cmd /k "%ROOT%tools\redis-8.10.3\start-redis.bat"
timeout /t 3 >nul

echo [2/3] 启动后端 (8081) ...
start "Backend 8081" cmd /k "%ROOT%server\start-server.bat"
timeout /t 10 >nul

echo [3/3] 启动前端 (5173) ...
start "Frontend 5173" cmd /k "cd /d %ROOT%web && npm run dev"

echo.
echo ----------------------------------------
echo  前端: http://localhost:5173
echo  后端: http://localhost:8081
echo  Redis: 127.0.0.1:6380 (db1)
echo ----------------------------------------
echo 依赖 MySQL 已在 3306 运行，库名 zhimi_life
echo.
pause
