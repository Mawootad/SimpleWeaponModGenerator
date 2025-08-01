package simpleweaponmodgenerator

import simpleweaponmodgenerator.schema.Manifest
import simpleweaponmodgenerator.schema.PatchEntry
import simpleweaponmodgenerator.schema.YamlAsset
import simpleweaponmodgenerator.schema.encodeToJson
import java.io.File
import java.lang.Exception
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipGenerator {
    fun generateZip(modPath: String, outPath: String) {
        if (!File("$modPath/generated/Blueprints").exists()) {
            println("Cannot generate patch zip, blueprint folder ${File("$modPath/generated/Blueprints").absolutePath} does not exist")
            return
        }

        if (File("$modPath/generated/Blueprints").listFiles().isEmpty()) {
            println("Cannot generate patch zip, blueprint folder ${File("$modPath/generated/Blueprints").absolutePath} is empty")
            return
        }

        var manifest: Manifest? = null

        val baseModPatches = buildList {
            for (assetFile in File(modPath).listFiles { it.extension == "asset" }) {
                try {
                    val monoBehavior = YamlAsset.parseMonoBehavior(assetFile)
                    if (monoBehavior.manifest != null) {
                        if (manifest != null) {
                            println("Cannot generate patch zip, multiple manifest files found in ${File(modPath).absolutePath}")
                            return
                        }
                        manifest = monoBehavior.manifest
                    }
                    if (monoBehavior.entries != null) {
                        addAll(monoBehavior.entries)
                    }
                } catch (e: Exception) {
                    println("Failed to parse manifest ${e.stackTraceToString()}")
                }
            }
        }

        if (manifest == null) {
            for (jsonFile in File(modPath).listFiles { it.extension == "json" }) {
                try {
                    manifest = Manifest.decodeJson(jsonFile)
                } catch (e: Exception) {
                }
            }

            println("Cannot generate patch zip, no manifest found in ${File(modPath).absolutePath}")
            return
        }

        if (manifest.uniqueName.isBlank()) {
            println("Cannot generate patch zip, manifest has no unique name")
            return
        }

        if (!File("$modPath/generated/generatedPatchesConfig.asset").exists()) {
            println("Cannot generate patch zip, missing generatedPatchesConfig.asset")
            return
        }

        val generatedPatchConfig =
            YamlAsset.parseMonoBehavior(File("$modPath/generated/generatedPatchesConfig.asset")).entries

        if (generatedPatchConfig.isNullOrEmpty()) {
            println("Cannot generate patch zip, generatedPatchesConfig.asset contains no patch entries")
            return
        }

        ZipOutputStream(File("$outPath/${manifest.uniqueName}.zip").outputStream()).use { stream ->
            val writer = stream.writer()

            fun flushAndClose() {
                writer.flush()
                stream.closeEntry()
            }

            stream.putNextEntry(ZipEntry("OwlcatModificationManifest.json"))
            writer.write(manifest.encodeToJson())
            flushAndClose()
            println("Added manifest")

            stream.putNextEntry(ZipEntry("OwlcatModificationSettings.json"))
            writer.write((baseModPatches + generatedPatchConfig).encodeToJson())
            flushAndClose()
            println("Added patch settings")

            if (File("$modPath/Blueprints").exists()) {
                for (blueprint in File("$modPath/Blueprints").listFiles { it.extension == "jbp" }) {
                    stream.putNextEntry(ZipEntry("Blueprints/${blueprint.name}"))
                    blueprint.reader().use { it.copyTo(writer) }
                    flushAndClose()
                }
                println("Added non-patch blueprints")
            }

            fun patchExtension(entry: PatchEntry) = if (entry.patchType == 2) "patch" else "jbp_patch"

            fun copyToFolder(file: File, folder: String) {
                stream.putNextEntry(ZipEntry("$folder/${file.name}"))
                file.reader().use { it.copyTo(writer) }
                flushAndClose()
            }

            for (patchEntry in baseModPatches) {
                val file = File("$modPath/Blueprints/${patchEntry.filename}.${patchExtension(patchEntry)}")
                if (!file.exists()) {
                    println("Patch Blueprints/${file.name} not found, ignoring")
                    continue
                }
                copyToFolder(file, "Blueprints")
            }
            if (baseModPatches.isNotEmpty()) println("Added non-generated patches")

            for (patchEntry in generatedPatchConfig) {
                val file = File("$modPath/generated/Blueprints/${patchEntry.filename}.${patchExtension(patchEntry)}")
                if (!file.exists()) {
                    println("Patch generated/Blueprints/${file.name} not found, ignoring")
                    continue
                }
                copyToFolder(file, "Blueprints")
            }
            if (generatedPatchConfig.isNotEmpty()) println("Added generated patches")

            if (File("$modPath/Localization").exists()) {
                for (localizationEntry in File("$modPath/Localization").listFiles { it.extension == "json" }) {
                    copyToFolder(localizationEntry, "Blueprints")
                }
                println("Added localization files")
            }
        }
        println("Created mod zip ${File("$outPath/${manifest.uniqueName}.zip").path}")
    }
}