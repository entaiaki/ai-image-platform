@echo off
setlocal enabledelayedexpansion

set BASE_URL=http://localhost:8080
set USERNAME=e2e_user
set PASSWORD=Passw0rd!
set PROMPT=E2E test cat

REM Step0: register (ignore errors)
curl -sS -X POST "%BASE_URL%/api/user/register" -H "Content-Type: application/json" -d "{\"username\":\"%USERNAME%\",\"password\":\"%PASSWORD%\"}" >nul 2>nul

REM Step1: login -> token
for /f "delims=" %%i in ('curl -sS -X POST "%BASE_URL%/api/user/login" -H "Content-Type: application/json" -d "{\"username\":\"%USERNAME%\",\"password\":\"%PASSWORD%\"}" ^| py -c "import sys,json;print(json.load(sys.stdin)['data']['token'])"') do set TOKEN=%%i

if "%TOKEN%"=="" (
  echo E2E FAIL: cannot get token. Is SpringBoot running on 8080?
  exit /b 1
)

echo TOKEN acquired

REM Step2: submit -> taskId
for /f "delims=" %%i in ('curl -sS -X POST "%BASE_URL%/api/tasks/submit" --get --data-urlencode "prompt=%PROMPT%" -H "Authorization: Bearer %TOKEN%" ^| py -c "import sys,json;print(json.load(sys.stdin)['data']['taskId'])"') do set TASK_ID=%%i

if "%TASK_ID%"=="" (
  echo E2E FAIL: cannot submit task.
  exit /b 2
)

echo Submitted taskId=%TASK_ID%

REM Step3: poll until DONE/FAILED
for /l %%i in (1,1,60) do (
  for /f "delims=" %%j in ('curl -sS "%BASE_URL%/api/tasks/%TASK_ID%" -H "Authorization: Bearer %TOKEN%"') do set RES=%%j

  for /f "delims=" %%s in ('echo !RES! ^| py -c "import sys,json;print(json.load(sys.stdin)['data']['status'])"') do set STATUS=%%s
  for /f "delims=" %%u in ('echo !RES! ^| py -c "import sys,json;d=json.load(sys.stdin)['data'];print(d.get('outputImageUrl') or '')"') do set OUT_URL=%%u
  for /f "delims=" %%p in ('echo !RES! ^| py -c "import sys,json;d=json.load(sys.stdin)['data'];print(d.get('outputLocalPath') or '')"') do set OUT_PATH=%%p

  echo [%%i] status=!STATUS! url=!OUT_URL! path=!OUT_PATH!

  if "!STATUS!"=="DONE" (
    if "!OUT_URL!"=="" if "!OUT_PATH!"=="" (
      echo E2E FAIL: DONE but output url/path empty
      exit /b 3
    )
    echo E2E PASS: DONE with output
    goto HISTORY
  )

  if "!STATUS!"=="FAILED" (
    echo E2E FAIL: task FAILED
    exit /b 4
  )

  timeout /t 2 >nul
)

:HISTORY
REM Step4: my history contains taskId
for /f "delims=" %%h in ('curl -sS "%BASE_URL%/api/tasks/my?limit=50" -H "Authorization: Bearer %TOKEN%"') do set HIS=%%h
for /f "delims=" %%f in ('echo !HIS! ^| py -c "import sys,json;arr=json.load(sys.stdin)['data'];tid=int('%TASK_ID%');print('1' if any(int(x['id'])==tid for x in arr) else '0')"') do set FOUND=%%f

if not "!FOUND!"=="1" (
  echo E2E FAIL: taskId not found in /my
  exit /b 5
)

echo E2E PASS: /my contains taskId
exit /b 0
