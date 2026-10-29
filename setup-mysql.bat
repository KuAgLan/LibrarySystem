@echo off
chcp 65001 >nul
setlocal

rem ============================================================
rem  一次性初始化：建库 + 建应用账号 + 灌示例数据 + 跑自测
rem  需要你输入 MySQL 的 root 密码（只在本次命令里用，不写盘）
rem ============================================================

set "MYSQL_BIN=C:\Program Files\MySQL\MySQL Server 8.0\bin"
if not exist "%MYSQL_BIN%\mysql.exe" (
  echo 没找到默认的 MySQL 路径：%MYSQL_BIN%
  set /p "MYSQL_BIN=请粘贴 MySQL 的 bin 目录："
)

if not exist "%MYSQL_BIN%\mysql.exe" (
  echo [!] 该目录下没有 mysql.exe，请检查路径
  exit /b 1
)

echo.
echo [1/3] 建库与账号（数据库 library / 账号 library / 密码 library123）
echo       下面提示 Enter password 时输入你的 MySQL root 密码：
"%MYSQL_BIN%\mysql.exe" -h 127.0.0.1 -P 3306 -u root -p -e "CREATE DATABASE IF NOT EXISTS library DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci; CREATE USER IF NOT EXISTS 'library'@'localhost' IDENTIFIED BY 'library123'; CREATE USER IF NOT EXISTS 'library'@'127.0.0.1' IDENTIFIED BY 'library123'; GRANT ALL PRIVILEGES ON library.* TO 'library'@'localhost'; GRANT ALL PRIVILEGES ON library.* TO 'library'@'127.0.0.1'; FLUSH PRIVILEGES;"
if errorlevel 1 (
  echo.
  echo [!] 建库失败：请确认 root 密码正确、MySQL80 服务在运行
  exit /b 1
)
echo      建库完成

if not exist library-system.jar (
  echo.
  echo [2/3] 还没构建，先跑 build.bat
  call build.bat
)

echo.
echo [2/3] 建表 + 灌示例数据
java -Dfile.encoding=UTF-8 -jar library-system.jar --init-db
java -Dfile.encoding=UTF-8 -jar library-system.jar --seed

echo.
echo [3/3] 自测
java -Dfile.encoding=UTF-8 -jar library-system.jar --selftest
if errorlevel 1 (
  echo [!] 自测没全过，把上面的输出发我
  exit /b 1
)

echo.
echo ============================================================
echo  全部就绪，直接运行：run.bat
echo  菜单里可以看图书 / 借书 / 还书 / 预约 / 报表
echo ============================================================
endlocal
