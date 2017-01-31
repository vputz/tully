rem
start cmd /c C:\vputz\influxdb-1.1.1-1\influxd.exe
rem
start cmd /c "C:\Program Files\MongoDB\Server\3.2\bin\mongod.exe"
rem
start cmd /c "java -cp c:\vputz\riemann-0.2.12\lib\riemann.jar riemann.bin start c:\vputz\riemann-0.2.12\etc\riemann.config"
