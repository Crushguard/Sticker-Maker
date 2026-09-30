package com.piptechnologies.stickermaker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * res/values/config.xml is the one place for the app's external configuration: the publisher's
 * support address and links. A key the code or the manifest reads
 * but config.xml lacks would only fail when resources merge, and the addresses also travel outside
 * the app (every pack WhatsApp receives names the publisher), so they are pinned here.
 */
class ConfigXmlTest {

    @Test
    fun `config xml parses and every entry is a non-blank untranslatable config_ string, once each`() {
        val entries = ConfigXml.entries
        assertTrue("config.xml has no strings", entries.isNotEmpty())
        for ((element, name) in entries) {
            assertTrue("$name: every key starts with config_", name.startsWith("config_"))
            assertEquals("$name: translatable=\"false\"", "false", element.getAttribute("translatable"))
            assertTrue("$name is blank", element.textContent.isNotBlank())
        }
        val duplicates = entries.groupingBy { it.second }.eachCount().filterValues { it > 1 }.keys
        assertTrue("config.xml defines these more than once: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `every config_ key the code and the manifest reference is defined`() {
        val referenced = referencedKeys()
        // The scan itself must find the known readers, or it would pass on nothing.
        assertTrue(
            "scan missed known references: $referenced",
            referenced.containsAll(
                listOf(
                    "config_support_email",
                    "config_privacy_policy_url",
                    "config_more_apps_url",
                    "config_firestore_database",
                ),
            ),
        )
        val missing = referenced - ConfigXml.strings.keys
        assertTrue("referenced but missing from config.xml: $missing", missing.isEmpty())
    }

    @Test
    fun `the support email is the publisher's address, never a personal one`() {
        val email = ConfigXml.strings.getValue("config_support_email")
        assertTrue("config_support_email \"$email\" is not an email address", EMAIL.matches(email))
        assertTrue("config_support_email \"$email\" is not on $PUBLISHER_DOMAIN", email.endsWith("@$PUBLISHER_DOMAIN"))
    }

    @Test
    fun `the privacy policy and more apps links are well formed`() {
        val strings = ConfigXml.strings
        assertTrue(strings.getValue("config_privacy_policy_url").startsWith("https://"))
        assertTrue(strings.getValue("config_more_apps_url").startsWith("https://play.google.com/store/apps/developer?id="))
    }

    @Test
    fun `the crash-reporting codes stay out of git, and both build scripts read them the same way`() {
        // The repository is public: the SDK project's access/secret pair lives in local.properties
        // (CI: secrets), never in a tracked file.
        for (path in listOf("app/src/main/res/values/config.xml", "settings.gradle.kts", "app/build.gradle.kts")) {
            assertTrue("$path holds a UUID literal; keep the crash-reporting codes in local.properties",
                UUID.find(ConfigXml.repoFile(path).readText()) == null)
        }
        val settings = ConfigXml.repoFile("settings.gradle.kts").readText()
        val app = ConfigXml.repoFile("app/build.gradle.kts").readText()
        for (source in listOf("crashReporting.accessCode", "crashReporting.secretCode", "CRASH_REPORTING_ACCESS_CODE", "CRASH_REPORTING_SECRET_CODE")) {
            assertTrue("settings.gradle.kts does not read $source", source in settings)
            assertTrue("app/build.gradle.kts does not read $source", source in app)
        }
    }

    @Test
    fun `the app, the rules deploy and the catalog functions use the same Firestore database`() {
        val database = ConfigXml.strings.getValue("config_firestore_database")
        // A named database like the other apps in the project (pdf-app, pedometer-app), never "(default)".
        assertTrue("config_firestore_database \"$database\" is not a named database id", DATABASE_ID.matches(database))
        val firebaseJson = ConfigXml.repoFile("firebase.json").readText()
        assertTrue("firebase.json deploys the Firestore rules to another database", "\"database\": \"$database\"" in firebaseJson)
        val functionsConfig = ConfigXml.repoFile("functions/src/config.js").readText()
        assertTrue("functions/src/config.js publishes to another database", "const DATABASE = '$database';" in functionsConfig)
    }

    @Test
    fun `every pack WhatsApp receives names the publisher's support address and privacy policy`() {
        val email = ConfigXml.strings.getValue("config_support_email")
        val privacy = ConfigXml.strings.getValue("config_privacy_policy_url")
        val packs = PackFixtures.packDirs.map { it.resolve("pack.json") }
        assertTrue("no pack fixtures found", packs.isNotEmpty())
        for (pack in packs + ConfigXml.repoFile("design/catalog.json")) {
            val json = pack.readText()
            val name = pack.parentFile?.name + "/" + pack.name
            assertEquals("$name publisher email", email, field(json, "publisher_email", "publisherEmail"))
            assertEquals("$name privacy policy", privacy, field(json, "privacy_policy_website", "privacyPolicyWebsite"))
        }
    }

    /** config_ names read as R.string.config_x in Kotlin or @string/config_x in XML, across the app's sources. */
    private fun referencedKeys(): Set<String> {
        val src = ConfigXml.moduleFile("src")
        val roots = listOf("main/kotlin", "main/res", "main/AndroidManifest.xml", "debug").map { src.resolve(it) }.filter { it.exists() }
        val configXml = ConfigXml.file.canonicalFile
        return roots.asSequence()
            .flatMap { it.walkTopDown() }
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") && it.canonicalFile != configXml }
            .flatMap { REFERENCE.findAll(it.readText()).map { match -> match.groupValues[1] } }
            .toSet()
    }

    /** The top-level string [snake] (pack.json) or [camel] (catalog.json) of a flat JSON file. */
    private fun field(json: String, snake: String, camel: String): String? =
        Regex("\"(?:$snake|$camel)\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)

    private companion object {
        const val PUBLISHER_DOMAIN = "piptechnologies.co"
        val EMAIL = Regex("[^@\\s]+@[^@\\s]+\\.[a-z]{2,}")
        val UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        val DATABASE_ID = Regex("[a-z][a-z0-9-]{2,62}[a-z0-9]")
        val REFERENCE = Regex("(?:R\\.string\\.|@string/)(config_\\w+)")
    }
}
