<#
  운영 서버에 발표용 문제를 미리 생성한다.

  실행 (레포 루트에서):
    powershell -ExecutionPolicy Bypass -File deploy\generate-problems.ps1

  - 테스트 계정 이메일/비밀번호는 실행 중에 직접 입력한다 (화면·파일·히스토리에 남지 않음).
  - 계정이 없으면 가입, 이미 있으면(409) 로그인한다.
  - 아래 조합마다 POST /api/problems/generate 를 호출한다 (조합당 3문제, OpenAI 비용 발생).
  - Cloudflare는 응답을 100초까지만 기다린다. 524가 떠도 서버는 생성을 계속하므로 마지막 집계를 보면 된다.
#>
param(
  [string]$BaseUrl = 'https://haru.careerpilot.kr',
  [int]$ProblemsPerSet = 3
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# all_seeds.json에 시드가 있는 조합만 사용
$combos = @(
  @{ category = 'Basic/Introductory';       subcategory = $null;                 difficulty = 0; language = 'Python' },
  @{ category = 'Basic/Introductory';       subcategory = $null;                 difficulty = 1; language = 'Python' },
  @{ category = 'Algorithm/Data Structure'; subcategory = 'Sort';                difficulty = 0; language = 'Python' },
  @{ category = 'Algorithm/Data Structure'; subcategory = 'Stack/Queue';         difficulty = 0; language = 'Python' },
  @{ category = 'Algorithm/Data Structure'; subcategory = 'Hash';                difficulty = 1; language = 'Python' },
  @{ category = 'Algorithm/Data Structure'; subcategory = 'Greedy';              difficulty = 1; language = 'Python' },
  @{ category = 'Algorithm/Data Structure'; subcategory = 'DFS/BFS';             difficulty = 1; language = 'Python' },
  @{ category = 'Algorithm/Data Structure'; subcategory = 'Binary Search';       difficulty = 1; language = 'Python' },
  @{ category = 'Algorithm/Data Structure'; subcategory = 'Dynamic Programming'; difficulty = 2; language = 'Python' },
  @{ category = 'Past Exam Prep';           subcategory = 'General';             difficulty = 1; language = 'Python' },
  @{ category = 'Basic/Introductory';       subcategory = $null;                 difficulty = 0; language = 'Java' },
  @{ category = 'Algorithm/Data Structure'; subcategory = 'Sort';                difficulty = 0; language = 'Java' }
)

function Invoke-HaruApi {
  param([string]$Method, [string]$Path, [hashtable]$Body, [string]$Token, [int]$TimeoutSec = 30)
  $headers = @{}
  if ($Token) { $headers['Authorization'] = "Bearer $Token" }
  $json = $Body | ConvertTo-Json -Depth 5 -Compress
  $bytes = [Text.Encoding]::UTF8.GetBytes($json)
  Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $headers `
    -ContentType 'application/json; charset=utf-8' -Body $bytes -TimeoutSec $TimeoutSec
}

function Get-HttpStatus($err) {
  try { return [int]$err.Exception.Response.StatusCode } catch { return 0 }
}

Write-Host "대상 서버: $BaseUrl"
$email = Read-Host '테스트 계정 이메일'
$secure = Read-Host '비밀번호 (영문+숫자 포함 8자 이상)' -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$password = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)

try {
  $res = Invoke-HaruApi -Method POST -Path '/api/auth/signup' -Body @{
    email = $email; password = $password; passwordConfirm = $password; nickname = 'haruseed'
  }
  Write-Host '새 테스트 계정으로 가입했습니다.'
} catch {
  if ((Get-HttpStatus $_) -eq 409) {
    $res = Invoke-HaruApi -Method POST -Path '/api/auth/login' -Body @{ email = $email; password = $password }
    Write-Host '이미 있는 계정이라 로그인했습니다.'
  } else {
    Write-Host ("가입 실패: HTTP {0} {1}" -f (Get-HttpStatus $_), $_.ErrorDetails.Message)
    exit 1
  }
}
$password = $null

$token = if ($res.data) { $res.data.accessToken } else { $res.accessToken }
if (-not $token) { Write-Host '토큰을 받지 못했습니다.'; exit 1 }

$total = 0
$i = 0
foreach ($c in $combos) {
  $i++
  $sub = if ($c.subcategory) { $c.subcategory } else { '-' }
  $label = "[{0,2}/{1}] {2} / {3} / 난이도 {4} / {5}" -f $i, $combos.Count, $c.category, $sub, $c.difficulty, $c.language
  $body = $c.Clone()
  $body['problemsPerSet'] = $ProblemsPerSet
  $sw = [Diagnostics.Stopwatch]::StartNew()
  try {
    $list = Invoke-HaruApi -Method POST -Path '/api/problems/generate' -Body $body -Token $token -TimeoutSec 180
    $n = @($list | Where-Object { $_ }).Count
    $total += $n
    Write-Host ("{0}  OK  {1}문제  {2:N0}초" -f $label, $n, $sw.Elapsed.TotalSeconds)
  } catch {
    $code = Get-HttpStatus $_
    Write-Host ("{0}  실패 HTTP {1}  {2:N0}초" -f $label, $code, $sw.Elapsed.TotalSeconds)
    if ($code -eq 524) { Write-Host '        (Cloudflare 100초 제한. 서버는 계속 생성하므로 마지막 집계에서 확인)' }
  }
}

$all = Invoke-RestMethod -Uri "$BaseUrl/api/problems" -TimeoutSec 30
$count = @($all.data | Where-Object { $_ }).Count
Write-Host ''
Write-Host ("이번 실행 응답 합계: {0}문제 / 서버 문제 은행 전체: {1}문제" -f $total, $count)
