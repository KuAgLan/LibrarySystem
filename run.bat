@echo off
chcp 65001 >nul

rem 运行入口：run.bat --seed / --demo / --selftest / 无参数进菜单
rem 默认连 127.0.0.1:3306/library，账号 library / library123
rem 想换连接可设置环境变量（不要加引号）：
rem   set "LIB_URL=jdbc:mysql://127.0.0.1:3306/library?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
rem   set "LIB_USER=root"
rem   set "LIB_PASSWORD=你的密码"

if not exist library-system.jar (
  echo [!] 还没有构建，先运行 setup-mysql.bat 或 build.bat
  exit /b 1
)

java -Dfile.encoding=UTF-8 -jar library-system.jar %*
