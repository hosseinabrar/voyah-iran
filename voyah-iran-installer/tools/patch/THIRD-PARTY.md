# Bundled patch tools

- APKTool 3.0.3: https://github.com/iBotPeaches/Apktool — Apache License 2.0, reproduced in APKTOOL-LICENSE.txt. The supplied APKTool archive is retained without modification.
- Eclipse Temurin OpenJDK 21.0.12.1+1, Windows x64: reduced runtime produced with jlink. Runtime legal notices are retained under java/legal and java/NOTICE in the payload. Source project: https://github.com/adoptium/temurin21-binaries and https://github.com/openjdk/jdk21u.
- Android platform-tools 37.0.1: original NOTICE.txt is included in the payload.

FrameworkPatch.java is our local patch orchestration/verification source. It is source-launched by the bundled Java runtime; the end user does not install Java, APKTool, Python or a compiler. No third-party tool is downloaded during installation.
