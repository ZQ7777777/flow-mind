$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$htmlPath = "C:\Users\zq\Desktop\resume\flow-mind\outputs\张琦-实习成果汇报.html"
$pptxPath = "C:\Users\zq\Desktop\resume\flow-mind\outputs\张琦-实习成果汇报.pptx"
$titles = @(
  '实习成果汇报',
  '我的实习主线，是把 Agent 生成做成工程闭环',
  '跨 7 个自然周，从流程运行时走向 Agent 全栈交付',
  'Agent 负责受限推理，确定性服务守住业务边界',
  '一次生成不等于完成，闭环验证才是工程交付',
  '复杂金标把“可生成”验证到“可运行”',
  '底座工作让 Agent 产物真正进入业务链路',
  '成长不只在技术栈，更在工程判断',
  '让 AI 能力落在可验证的业务结果上'
)
$future = '秋招将优先关注 Agent / AI 全栈方向；如果公司后续有相匹配的业务场景与岗位，也期待结合双方发展方向继续沟通。'

function Read-ZipText([System.IO.Compression.ZipArchive]$zip, [string]$entryName) {
  $entry = $zip.GetEntry($entryName)
  if ($null -eq $entry) { throw "Missing ZIP entry: $entryName" }
  $reader = [System.IO.StreamReader]::new($entry.Open(), [System.Text.Encoding]::UTF8)
  try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

function Extract-Text([string]$xml) {
  $matches = [regex]::Matches($xml, "<a:t>(.*?)</a:t>")
  return (($matches | ForEach-Object { [System.Net.WebUtility]::HtmlDecode($_.Groups[1].Value) }) -join "")
}

$html = [System.IO.File]::ReadAllText($htmlPath, [System.Text.Encoding]::UTF8)
$zip = [System.IO.Compression.ZipFile]::OpenRead($pptxPath)
try {
  $slideEntries = $zip.Entries | Where-Object { $_.FullName -match '^ppt/slides/slide\d+\.xml$' }
  $noteEntries = $zip.Entries | Where-Object { $_.FullName -match '^ppt/notesSlides/notesSlide\d+\.xml$' }
  $mediaEntries = $zip.Entries | Where-Object { $_.FullName -match '^ppt/media/' }
  if ($slideEntries.Count -ne 9) { throw "PPTX slide count is $($slideEntries.Count), expected 9" }
  if ($noteEntries.Count -ne 9) { throw "PPTX notes count is $($noteEntries.Count), expected 9" }
  if ($mediaEntries.Count -ne 2) { throw "PPTX media count is $($mediaEntries.Count), expected 2" }

  $shapeCount = 0
  $pictureCount = 0
  for ($i = 1; $i -le 9; $i += 1) {
    $slideXml = Read-ZipText $zip "ppt/slides/slide$i.xml"
    $shapeCount += ([regex]::Matches($slideXml, "<p:sp>")).Count
    $pictureCount += ([regex]::Matches($slideXml, "<p:pic>")).Count
    $slideText = Extract-Text $slideXml
    if (-not $slideText.Contains($titles[$i - 1])) { throw "PPTX slide $i is missing title: $($titles[$i - 1])" }
    if (-not $html.Contains($titles[$i - 1])) { throw "HTML is missing title: $($titles[$i - 1])" }
    $notesText = Extract-Text (Read-ZipText $zip "ppt/notesSlides/notesSlide$i.xml")
    if (-not $notesText.Contains("[Sources]")) { throw "PPTX slide $i notes missing [Sources]" }
  }

  $slide3Text = Extract-Text (Read-ZipText $zip "ppt/slides/slide3.xml")
  foreach ($metric in @("171", "142", "29")) {
    if (-not $slide3Text.Contains($metric) -or -not $html.Contains($metric)) { throw "Shared Git metric missing: $metric" }
  }
  $slide9Text = Extract-Text (Read-ZipText $zip "ppt/slides/slide9.xml")
  if (-not $slide9Text.Contains($future) -or -not $html.Contains($future)) { throw "Future-plan wording differs between formats" }

  $presentationXml = Read-ZipText $zip "ppt/presentation.xml"
  if ($presentationXml -notmatch 'cx="12192000"\s+cy="6858000"') { throw "PPTX is not 16:9 widescreen" }
  if ($pictureCount -ne 2 -or $shapeCount -lt 80) { throw "PPTX editability check failed: $shapeCount shapes, $pictureCount pictures" }

  Write-Output "PASS · HTML/PPTX parity: 9 titles, Git metrics, fixed future wording"
  Write-Output "PASS · PPTX structure: 9 slides, 9 notes, 2 images, 16:9, $shapeCount native shapes"
} finally {
  $zip.Dispose()
}
