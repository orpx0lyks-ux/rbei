$ErrorActionPreference = "Stop"
$tok = gh auth token
$repoRoot = Resolve-Path "$PSScriptRoot\.."
$wiki = Join-Path $repoRoot ".wiki-tmp"
Remove-Item -Recurse -Force $wiki -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $wiki | Out-Null
Copy-Item (Join-Path $repoRoot "docs\*.md") $wiki -Force
Push-Location $wiki
git init -b master
git add -A
git -c user.email="orpx0lyks-ux@users.noreply.github.com" -c user.name="orpx0lyks-ux" commit -m "Sync RBEI wiki from docs/"
git remote add origin "https://x-access-token:$tok@github.com/orpx0lyks-ux/rbei.wiki.git"
git push -u origin master --force
Pop-Location
Write-Host "Done: https://github.com/orpx0lyks-ux/rbei/wiki"
