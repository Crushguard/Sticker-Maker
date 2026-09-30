package com.piptechnologies.stickermaker

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** res/values/config.xml read the way the unit tests need it: no Android resources on the JVM. */
internal object ConfigXml {

    /** Every <string> in config.xml, in file order, duplicates included. */
    val entries: List<Pair<Element, String>> by lazy {
        val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            .getElementsByTagName("string")
        (0 until strings.length).map { strings.item(it) as Element }.map { it to it.getAttribute("name") }
    }

    /** name to value. */
    val strings: Map<String, String> by lazy {
        entries.associate { (element, name) -> name to element.textContent.trim() }
    }

    val file: File by lazy { moduleFile("src/main/res/values/config.xml") }

    /** [path] inside the app module. Gradle runs unit tests from the module directory; accept the repository root too. */
    fun moduleFile(path: String): File =
        listOf(File(path), File("app", path)).firstOrNull { it.exists() }
            ?: error("$path not found from ${File(".").absolutePath}")

    /** [path] from the repository root (the directory holding packs/). */
    fun repoFile(path: String): File = File(PackFixtures.packsDir.parentFile, path)
}
