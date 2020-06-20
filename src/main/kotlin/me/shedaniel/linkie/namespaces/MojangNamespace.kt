package me.shedaniel.linkie.namespaces

import kotlinx.serialization.json.content
import me.shedaniel.linkie.*
import me.shedaniel.linkie.utils.onlyClass
import me.shedaniel.linkie.utils.remapMethodDescriptor
import me.shedaniel.linkie.utils.toVersion
import me.shedaniel.linkie.utils.tryToVersion
import java.io.InputStream
import java.net.URL

object MojangNamespace : Namespace("mojang") {
    val versionJsonMap = mutableMapOf<String, String>()
    private var latestRelease = ""

    init {
        registerSupplier(simpleCachedSupplier("1.14.4") {
            MappingsContainer(it, name = "Mojang").apply {
                readMojangMappings(
                        client = "https://launcher.mojang.com/v1/objects/c0c8ef5131b7beef2317e6ad80ebcd68c4fb60fa/client.txt",
                        server = "https://launcher.mojang.com/v1/objects/448ccb7b455f156bb5cb9cdadd7f96cd68134dbd/server.txt"
                )
                mappingSource = MappingsContainer.MappingSource.MOJANG
            }
        })
        registerSupplier(multipleCachedSupplier({ versionJsonMap.keys }, { it }) {
            MappingsContainer(it, name = "Mojang").apply {
                val url = URL(versionJsonMap[version])
                val versionJson = json.parseJson(url.readText()).jsonObject
                val downloads = versionJson["downloads"]!!.jsonObject
                readMojangMappings(
                        client = downloads["client_mappings"]!!.jsonObject["url"]!!.content,
                        server = downloads["server_mappings"]!!.jsonObject["url"]!!.content
                )
                mappingSource = MappingsContainer.MappingSource.MOJANG
            }
        })
    }

    override fun getDefaultLoadedVersions(): List<String> = listOf(latestRelease)

    override fun getAllVersions(): List<String> =
            versionJsonMap.keys.toMutableList().apply { add("1.14.4") }

    override fun reloadData() {
        versionJsonMap.clear()
        val versionManifest = json.parseJson(URL("https://launchermeta.mojang.com/mc/game/version_manifest.json").readText())
        val `19w36a` = "19w36a".toVersion()
        versionManifest.jsonObject["versions"]!!.jsonArray.forEach { versionElement ->
            val versionString = versionElement.jsonObject["id"]!!.content
            val version = versionString.tryToVersion() ?: return@forEach
            if (version >= `19w36a`) {
                val urlString = versionElement.jsonObject["url"]!!.content
                versionJsonMap[versionString] = urlString
            }
        }
        latestRelease = versionManifest.jsonObject["latest"]!!.jsonObject["release"]!!.content
    }

    override fun getDefaultVersion(command: String?, channelId: Long?): String = latestRelease

    private fun MappingsContainer.readMojangMappings(client: String, server: String) {
        val invokes: MutableList<() -> Unit> = mutableListOf()
        invokes.addAll(readMappings(URL(client).openStream()))
        invokes.addAll(readMappings(URL(server).openStream()))
        invokes.forEach { it() }
    }

    private fun MappingsContainer.readMappings(inputStream: InputStream): List<() -> Unit> {
        val invokes: MutableList<() -> Unit> = mutableListOf()
        fun String.toActualDescription(): String = when (this) {
            "boolean" -> "Z"
            "char" -> "C"
            "byte" -> "B"
            "short" -> "S"
            "int" -> "I"
            "float" -> "F"
            "long" -> "J"
            "double" -> "D"
            "void" -> "V"
            "" -> ""
            else -> "L${replace('.', '/')};"
        }

        fun getActualDescription(body: String, returnType: String): String {
            val splitClass = body.trimStart('(').trimEnd(')').split(',')
            return "(${splitClass.joinToString("") { it.toActualDescription() }})${returnType.toActualDescription()}"
        }

        var lastClass: Class? = null
        inputStream.bufferedReader().forEachLine {
            if (it.startsWith('#')) return@forEachLine
            if (it.startsWith("    ")) {
                val s = it.trimIndent().split(':')
                if (s.size >= 3 && s[0].toIntOrNull() != null && s[1].toIntOrNull() != null) {
                    val split = s.drop(2).joinToString(":").split(' ').toMutableList()
                    split.remove("->")
                    lastClass!!.apply {
                        val methodName = split[1].substring(0, split[1].indexOf('('))
                        getOrCreateMethod(methodName, getActualDescription(split[1].substring(methodName.length), split[0])).apply {
                            obfName.merged = split[2]
                            invokes.add {
                                obfDesc.merged = obfDesc.merged ?: intermediaryDesc.remapMethodDescriptor { descClass ->
                                    getClass(descClass)?.obfName?.merged ?: descClass
                                }
                            }
                        }
                    }
                } else {
                    val split = it.trimIndent().replace(" -> ", " ").split(' ')
                    lastClass!!.apply {
                        getOrCreateField(split[1], split[0].toActualDescription()).apply {
                            obfName.merged = split[2]
                            invokes.add {
                                obfDesc.merged = obfDesc.merged ?: intermediaryDesc.remapMethodDescriptor { descClass ->
                                    getClass(descClass)?.obfName?.merged ?: descClass
                                }
                            }
                        }
                    }
                }
            } else {
                val split = it.trimIndent().trimEnd(':').split(" -> ")
                val className = split[0].replace('.', '/')
                val obf = split[1]
                if (className.onlyClass() != "package-info") {
                    getOrCreateClass(className).apply {
                        obfName.merged = obfName.merged ?: obf
                        lastClass = this
                    }
                }
            }
        }
        return invokes
    }
}