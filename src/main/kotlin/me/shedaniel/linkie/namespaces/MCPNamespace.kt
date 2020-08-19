package me.shedaniel.linkie.namespaces

import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.multipleCachedSupplier
import me.shedaniel.linkie.simpleCachedSupplier
import me.shedaniel.linkie.utils.Version
import me.shedaniel.linkie.utils.toVersion
import me.shedaniel.linkie.utils.tryToVersion
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.util.Comparator
import java.util.zip.ZipInputStream

object MCPNamespace : Namespace("mcp") {
    private val mcpConfigSnapshots = mutableMapOf<Version, MutableList<String>>()

    init {
        registerSupplier(multipleCachedSupplier({ getAllBotVersions() }, {
            "$it-${mcpConfigSnapshots[it.toVersion()]?.max()!!}"
        }) {
            MappingsContainer(it, name = "MCP").apply {
                val latestSnapshot = mcpConfigSnapshots[it.toVersion()]?.max()!!
                mappingSource = if (it.toVersion() >= Version(1, 13)) {
                    loadTsrgFromURLZip(URL("http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/$it/mcp_config-$it.zip"))
                    MappingsContainer.MappingSource.MCP_TSRG
                } else {
                    loadSrgFromURLZip(URL("http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp/$it/mcp-$it-srg.zip"))
                    MappingsContainer.MappingSource.MCP_SRG
                }
                loadMCPFromURLZip(URL("http://export.mcpbot.bspk.rs/mcp_snapshot/$latestSnapshot-$it/mcp_snapshot-$latestSnapshot-$it.zip"))
            }
        })
        registerSupplier(simpleCachedSupplier("1.16", "1.16-20200514") {
            MappingsContainer(it, name = "MCP").apply {
                loadTsrgFromURLZip(URL("http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/1.16/mcp_config-1.16.zip"))
                loadMCPFromURLZip(URL("https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_snapshot/20200514-1.16/mcp_snapshot-20200514-1.16.zip"))
                mappingSource = MappingsContainer.MappingSource.MCP_TSRG
            }
        })
        registerSupplier(simpleCachedSupplier("1.16.1", "1.16.1-20200723") {
            MappingsContainer(it, name = "MCP").apply {
                loadTsrgFromURLZip(URL("http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/1.16.1/mcp_config-1.16.1.zip"))
                loadMCPFromURLZip(URL("https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_snapshot/20200723-1.16.1/mcp_snapshot-20200723-1.16.1.zip"))
                mappingSource = MappingsContainer.MappingSource.MCP_TSRG
            }
        })
        registerSupplier(simpleCachedSupplier("1.16.2", "1.16.2-20200723") {
            MappingsContainer(it, name = "MCP").apply {
                loadTsrgFromURLZip(URL("http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/1.16.2/mcp_config-1.16.2.zip"))
                loadMCPFromURLZip(URL("https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_snapshot/20200723-1.16.1/mcp_snapshot-20200723-1.16.1.zip"))
                mappingSource = MappingsContainer.MappingSource.MCP_TSRG
            }
        })
    }

    override fun supportsFieldDescription(): Boolean = false
    override fun getDefaultLoadedVersions(): List<String> = listOf(getDefaultVersion())
    fun getAllBotVersions(): List<String> = mcpConfigSnapshots.keys.map { it.toString() }
    override fun getAllVersions(): List<String> = getAllBotVersions().toMutableList().also { it.addAll(listOf("1.16", "1.16.1", "1.16.2")) }
    override fun getDefaultVersion(channel: String): String = getAllVersions().maxWith(Comparator.nullsFirst(compareBy { it.tryToVersion() }))!!

    override fun supportsAT(): Boolean = true
    override fun reloadData() {
        mcpConfigSnapshots.clear()
        json.parseJson(URL("http://export.mcpbot.bspk.rs/versions.json").readText()).jsonObject.forEach { mcVersion, mcpVersionsObj ->
            val list = mcpConfigSnapshots.getOrPut(mcVersion.toVersion(), { mutableListOf() })
            mcpVersionsObj.jsonObject["snapshot"]?.jsonArray?.forEach {
                list.add(it.primitive.content)
            }
        }
        mcpConfigSnapshots.filterValues { it.isEmpty() }.keys.toMutableList().forEach { mcpConfigSnapshots.remove(it) }
    }

    private fun MappingsContainer.loadTsrgFromURLZip(url: URL) {
        val stream = ZipInputStream(url.openStream())
        while (true) {
            val entry = stream.nextEntry ?: break
            if (!entry.isDirectory && entry.name.split("/").lastOrNull() == "joined.tsrg") {
                loadTsrgFromInputStream(stream)
                break
            }
        }
    }

    private fun MappingsContainer.loadSrgFromURLZip(url: URL) {
        val stream = ZipInputStream(url.openStream())
        while (true) {
            val entry = stream.nextEntry ?: break
            if (!entry.isDirectory && entry.name.split("/").lastOrNull() == "joined.srg") {
                loadSrgFromInputStream(stream)
                break
            }
        }
    }

    private fun MappingsContainer.loadSrgFromInputStream(stream: InputStream) {
        val lines = InputStreamReader(stream).readLines().groupBy { it.split(' ')[0] }
        lines["CL:"]?.forEach { classLine ->
            val split = classLine.substring(4).split(" ")
            val obf = split[0]
            val named = split[1]
            getOrCreateClass(named).apply {
                obfName.merged = obf
            }
        }
        lines["FD:"]?.forEach { fieldLine ->
            val split = fieldLine.substring(4).split(" ")
            val obfClass = split[0].substring(0, split[0].lastIndexOf('/'))
            val obf = split[0].substring(obfClass.length + 1)
            val namedClass = split[1].substring(0, split[1].lastIndexOf('/'))
            val intermediary = split[1].substring(namedClass.length + 1)
            getClass(namedClass)?.apply {
                getOrCreateField(intermediary, "").apply {
                    obfName.merged = obf
                    obfDesc.merged = ""
                }
            }
        }
        lines["MD:"]?.forEach { fieldLine ->
            val split = fieldLine.substring(4).split(" ")
            val obfClass = split[0].substring(0, split[0].lastIndexOf('/'))
            val obf = split[0].substring(obfClass.length + 1)
            val obfDesc = split[1]
            val namedClass = split[2].substring(0, split[2].lastIndexOf('/'))
            val intermediary = split[2].substring(namedClass.length + 1)
            val namedDesc = split[3]
            getClass(namedClass)?.apply {
                getOrCreateMethod(intermediary, namedDesc).also { method ->
                    method.obfName.merged = obf
                    method.obfDesc.merged = obfDesc
                }
            }
        }
    }

    private fun MappingsContainer.loadTsrgFromInputStream(stream: InputStream) {
        var lastClass: String? = null
        InputStreamReader(stream).forEachLine {
            val split = it.trimIndent().split(" ")
            if (!it.startsWith('\t')) {
                val obf = split[0]
                val named = split[1]
                getOrCreateClass(named).apply {
                    obfName.merged = obf
                }
                lastClass = named
            } else {
                val clazz = lastClass?.let(this::getOrCreateClass) ?: return@forEachLine
                when (split.size) {
                    2 -> {
                        val obf = split[0]
                        val tsrg = split[1]
                        clazz.apply {
                            getOrCreateField(tsrg, "").apply {
                                obfName.merged = obf
                                obfDesc.merged = ""
                            }
                        }
                    }
                    3 -> {
                        val obf = split[0]
                        val obfDesc = split[1]
                        val tsrg = split[2]
                        clazz.apply {
                            getOrCreateMethod(tsrg, "").also { method ->
                                method.obfName.merged = obf
                                method.obfDesc.merged = obfDesc
                            }
                        }
                    }
                }
            }
        }
    }

    private fun MappingsContainer.loadMCPFromURLZip(url: URL) {
        val stream = ZipInputStream(url.openStream())
        while (true) {
            val entry = stream.nextEntry ?: break
            if (!entry.isDirectory) {
                when (entry.name.split("/").lastOrNull()) {
                    "fields.csv" -> loadMCPFieldsCSVFromInputStream(stream)
                    "methods.csv" -> loadMCPMethodsCSVFromInputStream(stream)
                }
            }
        }
        stream.close()
    }

    private fun MappingsContainer.loadMCPFieldsCSVFromInputStream(stream: InputStream) {
        val map = mutableMapOf<String, String>()
        stream.bufferedReader().lineSequence().forEach {
            val split = it.split(',')
            map[split[0]] = split[1]
        }
        classes.forEach {
            it.fields.forEach { field ->
                map[field.intermediaryName]?.apply {
                    field.mappedName = this
                    field.mappedDesc = ""
                }
            }
        }
    }

    private fun MappingsContainer.loadMCPMethodsCSVFromInputStream(stream: InputStream) {
        val map = mutableMapOf<String, String>()
        stream.bufferedReader().lineSequence().forEach {
            val split = it.split(',')
            map[split[0]] = split[1]
        }
        classes.forEach {
            it.methods.forEach {
                map[it.intermediaryName]?.apply {
                    it.mappedName = this
                    it.mappedDesc = ""
                }
            }
        }
    }
}