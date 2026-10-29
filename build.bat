@echo off
chcp 65001 >nul
setlocal

rem 一键构建：编译源码 → 打包 library-system.jar（内含 Class-Path 指向驱动）

set JAVA_BIN=java
where javac >nul 2>&1
if errorlevel 1 (
  echo [!] 没找到 javac，请先安装 JDK 17 并把它加进 PATH
  exit /b 1
)

if not exist lib\mysql-connector-j-8.0.33.jar (
  echo [!] 缺少 lib\mysql-connector-j-8.0.33.jar，请先放到 lib 目录
  exit /b 1
)

if exist out rmdir /s /q out
mkdir out

echo [1/2] 编译...
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -cp "lib\mysql-connector-j-8.0.33.jar" -d out @sources.txt
del sources.txt
if errorlevel 1 (
  echo [!] 编译失败
  exit /b 1
)

echo [2/2] 打包...
> manifest.txt echo Main-Class: library.Main
>> manifest.txt echo Class-Path: lib/mysql-connector-j-8.0.33.jar
jar --create --file library-system.jar --manifest manifest.txt -C out .
del manifest.txt

echo.
echo 构建完成：library-system.jar
echo 运行： java -jar library-system.jar --help
