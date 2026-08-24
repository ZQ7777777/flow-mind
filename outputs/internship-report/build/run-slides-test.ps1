$ErrorActionPreference = "Stop"
$env:RUNTIME_NODE = "C:\Users\zq\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe"
$env:RUNTIME_NODE_MODULES = "C:\Users\zq\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules"
$env:RUNTIME_BIN_DIR = "C:\Users\zq\.cache\codex-runtimes\codex-primary-runtime\dependencies\bin\override"

& "C:\Users\zq\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" `
  "C:\Users\zq\Desktop\resume\flow-mind\outputs\internship-report\build\slides-test-windows-wrapper.py"

exit $LASTEXITCODE
