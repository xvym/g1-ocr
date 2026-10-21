param([switch]$UseLocalTools)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (Test-Path -LiteralPath '.tools/jdk21.0.9_10/bin/java.exe') {
        $env:JAVA_HOME = (Resolve-Path '.tools/jdk21.0.9_10').Path
    }
    if ($UseLocalTools) {
        # Official MSVC VSIX and Windows SDK NuGet packages extracted under .tools/native.
        $toolsRoot = (Resolve-Path '.tools/native').Path
        $compilerRoot = "$toolsRoot/vs/VC/Tools/MSVC/14.44.35207"
        $sdkRoot = "$toolsRoot/microsoft.windows.sdk.cpp/c"
        $sdkLibraries = "$toolsRoot/microsoft.windows.sdk.cpp.x64/c"
        $cmakeRoot = "$toolsRoot/vs/Common7/IDE/CommonExtensions/Microsoft/CMake"
        $env:PATH = "$compilerRoot/bin/Hostx64/x64;$sdkRoot/bin/10.0.26100.0/x64;$cmakeRoot/Ninja;$env:PATH"
        $env:INCLUDE = "$compilerRoot/include;$sdkRoot/Include/10.0.26100.0/ucrt;$sdkRoot/Include/10.0.26100.0/shared;$sdkRoot/Include/10.0.26100.0/um;$sdkRoot/Include/10.0.26100.0/winrt;$sdkRoot/Include/10.0.26100.0/cppwinrt"
        $env:LIB = "$compilerRoot/lib/x64;$compilerRoot/lib/onecore/x64;$sdkLibraries/um/x64;$sdkLibraries/ucrt/x64"
        $cmake = "$cmakeRoot/CMake/bin/cmake.exe"
        & $cmake -S native/capture -B output/native-local -G 'Ninja Multi-Config' '-DCMAKE_CXX_COMPILER=cl'
        $nativeOutput = 'output/native-local'
    } else {
        $cmake = 'cmake'
        & $cmake -S native/capture -B output/native -G 'Visual Studio 17 2022' -A x64 '-DCMAKE_SYSTEM_VERSION=10.0.26100.0'
        $nativeOutput = 'output/native'
    }
    if ($LASTEXITCODE -ne 0) { throw "CMake configuration failed: $LASTEXITCODE" }
    & $cmake --build $nativeOutput --config Release
    if ($LASTEXITCODE -ne 0) { throw "Native build failed: $LASTEXITCODE" }
    if ($UseLocalTools) {
        New-Item -ItemType Directory -Force 'output/native/Release' | Out-Null
        Copy-Item -LiteralPath "$nativeOutput/Release/g1capture.dll" -Destination 'output/native/Release/g1capture.dll'
    }
} finally {
    Pop-Location
}
