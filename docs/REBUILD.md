# Rebuilding the ShadowClanGaming jar without Gradle

Gradle/NeoForm is unusable here (vineflower hardcodes -Xmx4096m and the box has 1 GB), so the
classpath is rebuilt by hand. Everything below lives in /home/user/.cache/scg, which is NOT
persisted between sessions - redo it if it is missing.

1. JDK 21
   curl -sL -o jdk.tar.gz https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse
   tar xzf jdk.tar.gz && mv jdk-21* jdk

2. NeoForm config (1.21.1-20240808.144430) + NFRT
   curl -sL -o neoform.zip https://maven.neoforged.net/releases/net/neoforged/neoform/1.21.1-20240808.144430/neoform-1.21.1-20240808.144430.zip
   unzip -oq neoform.zip -d neoform
   curl -sL -o nfrt.jar https://maven.neoforged.net/releases/net/neoforged/neoform-runtime/2.0.31/neoform-runtime-2.0.31-all.jar

3. Trim the neoform config so it stops at `rename` (no decompile - it cannot fit in 1 GB), and add
   three dummy steps named decompile/inject/patch of type `strip`, because NFRT insists on that tail.
   Zip it as neoform-nfrt2.zip.

4. Run the pipeline, producing the renamed (mojmap) joined jar:
   jdk/bin/java -jar nfrt.jar run --neoform neoform-nfrt2.zip --dist joined \
       --write-result=vanillaDeobfuscated:rename.jar --home-dir nfhome --work-dir nfwork --java-home jdk

5. Apply NeoForge's binary patches (ATs/patches: SavedData.Factory ctor, Projectile ctor,
   Level#getCapability, VillagerTrades.ItemsForEmeralds, ...):
   curl -sL -o userdev.jar https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.264/neoforge-21.1.264-userdev.jar
   unzip -o userdev.jar joined.lzma
   curl -sL -o binarypatcher.jar https://maven.neoforged.net/releases/net/minecraftforge/binarypatcher/1.1.1/binarypatcher-1.1.1-fatjar.jar
   jdk/bin/java -jar binarypatcher.jar --clean rename.jar --output patched.jar --apply joined.lzma

6. Libraries: all 47 from the installer's version.json + the 97 from the 1.21.1 version json +
   neoforge-21.1.264-universal.jar + jetbrains annotations + jsr305. cp.txt holds the finished string.

7. Build:
   jdk/bin/javac -nowarn -d /tmp/buildout -cp "$(cat cp.txt)" $(find src/main/java -name '*.java')
   then jar the classes + src/main/resources.
