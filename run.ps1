param([switch]$Test, [string]$DataPath)
$ErrorActionPreference = 'Stop'
$compiler = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/javac.exe' } else { (Get-Command javac -ErrorAction Stop).Source }
$runtime = Join-Path (Split-Path $compiler) 'java.exe'
$sourceFiles = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
if ($Test) { $sourceFiles += @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'test') -Recurse -Filter '*.java' | ForEach-Object FullName) }
$outputPath = Join-Path $PSScriptRoot 'out'
& $compiler --release 17 -Xlint:all -d $outputPath @sourceFiles
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($Test) { & $runtime -cp $outputPath bankapp.RegressionTests }
else {
    if (!$DataPath) { $DataPath = Join-Path $PSScriptRoot 'data' }
    & $runtime -cp $outputPath bankapp.App $DataPath
}
exit $LASTEXITCODE
