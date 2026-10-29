@echo off
chcp 65001 >nul

rem 运行入口：run.bat --seed / --demo / --selftest / 无参数进菜单
rem 连接参数可通过环境变量覆盖：
rem   set LIB_URL=jdbc:mysql://127.0.0.1:3306/library?useSSL=false&serverTimezone=Asia/Shanghai
rem   set LIB_USER=root
rem   set LIB_PASSWORD=你的密码

if not exist library-system.jar (
  echo [!] 还没有构建，先运行 build.bat
  exit /b 1
)

java -Dfile.encoding=UTF-8 -jar library-system.jar %*
