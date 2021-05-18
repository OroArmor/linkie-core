package me.shedaniel.linkie.parser.proguard

import me.shedaniel.linkie.MappingsSource
import me.shedaniel.linkie.parser.AbstractParser
import me.shedaniel.linkie.parser.ArrayEntryComplex
import me.shedaniel.linkie.parser.MappingsVisitor
import me.shedaniel.linkie.utils.onlyClass

fun proguard(content: String): ProguardParser = ProguardParser(content)

class ProguardParser(content: String) : AbstractParser<ArrayEntryComplex>(::ArrayEntryComplex) {
    companion object {
        const val NS_OBF = "obf"
        const val NS_MAPPED = "mapped"
    }

    val lines = content.lineSequence()
    override val namespaces = mutableMapOf(
        NS_OBF to 0,
        NS_MAPPED to 1,
    )

    override val source: MappingsSource
        get() = MappingsSource.PROGUARD

    override fun parse(visitor: MappingsVisitor) = withVisitor(visitor) {
        visitor.visitSelfStart()

        lines.forEach { line ->
            if (line.startsWith('#') || line.isBlank()) return@forEach
            if (line.startsWith("    ")) {
                val trim = line.trimIndent().substringAfterLast(':')
                val obf = trim.substringAfterLast(" -> ")
                val type = trim.substringBefore(' ')
                val self = trim.substring((type.length + 1) until (trim.length - 4 - obf.length))
                if (trim.contains('(')) {
                    lastClassVisitor?.also { visitor ->
                        val methodName = self.substringBefore('(')
                        lastMethod[NS_OBF] = obf
                        lastMethod[NS_MAPPED] = methodName
                        visitor.visitMethod(lastMethod, getActualDescription(self.substring(methodName.length), type))
                    }
                } else {
                    lastClassVisitor?.also { visitor ->
                        lastField[NS_OBF] = obf
                        lastField[NS_MAPPED] = self
                        visitor.visitField(lastField, type.delocalizeDescriptorType())
                    }
                }
            } else {
                val split = line.trimIndent().trimEnd(':').split(" -> ")
                val className = split[0].replace('.', '/')
                val obf = split[1]
                if (className.onlyClass() != "package-info") {
                    lastClass[NS_OBF] = obf
                    lastClass[NS_MAPPED] = className
                    lastClassVisitor = visitor.visitClass(lastClass)
                }
            }
        }

        visitor.visitEnd()
    }

    fun String.delocalizeDescriptorType(): String = when (this) {
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
        val splitClass = body.trimStart('(').trimEnd(')').splitToSequence(',')
        return "(${splitClass.joinToString("") { it.delocalizeDescriptorType() }})${returnType.delocalizeDescriptorType()}"
    }
}
