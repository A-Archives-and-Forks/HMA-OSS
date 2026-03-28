package icu.nullptr.hidemyapplist.common

import icu.nullptr.hidemyapplist.common.Constants.ENABLE_INTERNET_UNKNOWN
import icu.nullptr.hidemyapplist.common.settings_presets.ReplacementItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.frknkrc44.hma_oss.common.BuildConfig

@Serializable
data class JsonConfig(
    var configVersion: Int = BuildConfig.CONFIG_VERSION,
    var detailLog: Boolean = false,
    var errorOnlyLog: Boolean = false,
    var maxLogSize: Int = 512,
    var forceMountData: Boolean = true,
    var disableActivityLaunchProtection: Boolean = false,
    var altAppDataIsolation: Boolean = false,
    var altVoldAppDataIsolation: Boolean = false,
    var skipSystemAppDataIsolation: Boolean = true,
    var packageQueryWorkaround: Boolean = false,
    var enableInternet: Int = ENABLE_INTERNET_UNKNOWN,
    val templates: MutableMap<String, Template> = mutableMapOf(),
    val settingsTemplates: MutableMap<String, SettingsTemplate> = mutableMapOf(),
    val disabledHooks: MutableList<HookItem> = mutableListOf(),
    val scope: MutableMap<String, AppConfig> = mutableMapOf()
) {
    @Serializable
    data class Template(
        val isWhitelist: Boolean,
        val appList: Set<String>
    ) {
        override fun toString() = encoder.encodeToString(this)
    }

    @Serializable
    data class SettingsTemplate(
        val settingsList: Set<ReplacementItem>
    ) {
        override fun toString() = encoder.encodeToString(this)
    }

    @Serializable
    data class AppConfig(
        var useWhitelist: Boolean = false,
        var excludeSystemApps: Boolean = true,
        var hideInstallationSource: Boolean = false,
        var hideSystemInstallationSource: Boolean = false,
        var excludeTargetInstallationSource: Boolean = false,
        var invertActivityLaunchProtection: Boolean = false,
        var excludeVoldIsolation: Boolean = false,
        var restrictedZygotePermissions: List<Int> = listOf(),
        var applyTemplates: MutableSet<String> = mutableSetOf(),
        var applyPresets: MutableSet<String> = mutableSetOf(),
        var applySettingTemplates: MutableSet<String> = mutableSetOf(),
        var applySettingsPresets: MutableSet<String> = mutableSetOf(),
        var extraAppList: MutableSet<String> = mutableSetOf(),
        var extraOppositeAppList: MutableSet<String> = mutableSetOf(),
    ) {
        override fun toString() = encoder.encodeToString(this)

        companion object {
            fun parse(json: String) = encoder.decodeFromString<AppConfig>(json)
        }
    }

    @Serializable
    data class HookItem(
        val className: String,
        val methodName: String,
        val argumentCount: Int,
    ) {
        override fun toString() = encoder.encodeToString(this)

        companion object {
            fun parse(json: String) = encoder.decodeFromString<HookItem>(json)
        }
    }

    companion object {
        fun parse(json: String) = encoder.decodeFromString<JsonConfig>(json)

        val encoder = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }

    override fun toString() = encoder.encodeToString(this)
}
