package com.cprint.app.driver

import java.util.Locale

/** IEEE-1284 identity parsing and conservative driver selection. */
object PrinterDriverRouter {
    enum class Family { ESCPR, GUTENPRINT, HPLIP, SPLIX, UNSUPPORTED }

    data class Identity(
        val raw: String?,
        val manufacturer: String?,
        val model: String?,
        val commands: Set<String>
    )

    data class Route(
        val family: Family,
        val pack: DriverPackager.DriverPack?,
        val modelId: String?,
        val reason: String
    )

    fun parseDeviceId(raw: String?): Identity {
        val fields = raw.orEmpty()
            .split(';')
            .mapNotNull { token ->
                val separator = token.indexOf(':').takeIf { it >= 0 }
                    ?: token.indexOf('=').takeIf { it >= 0 }
                    ?: return@mapNotNull null
                token.substring(0, separator).trim().uppercase(Locale.US) to token.substring(separator + 1).trim()
            }
            .toMap()
        val commandText = fields["CMD"] ?: fields["COMMAND SET"] ?: ""
        return Identity(
            raw = raw?.trim()?.takeIf { it.isNotEmpty() },
            manufacturer = fields["MFG"] ?: fields["MANUFACTURER"],
            model = fields["MDL"] ?: fields["MODEL"],
            commands = commandText.split(',').map { it.trim().uppercase(Locale.US) }.filter { it.isNotEmpty() }.toSet()
        )
    }

    fun route(vendorId: Int, identity: Identity): Route {
        val manufacturer = identity.manufacturer.orEmpty().uppercase(Locale.US)
        val model = identity.model.orEmpty()
        val modelUpper = model.uppercase(Locale.US)
        return when {
            vendorId == EPSON_VID || manufacturer.contains("EPSON") -> Route(
                family = Family.ESCPR,
                pack = DriverPackager.DRV_ESCPR,
                modelId = epsonModelId(model) ?: "L3118",
                reason = "IEEE-1284 identifies an Epson printer"
            )
            vendorId == HP_VID || manufacturer.contains("HEWLETT") || manufacturer == "HP" -> Route(
                Family.HPLIP, DriverPackager.DRV_HPLIP, model.takeIf { it.isNotBlank() },
                "IEEE-1284 identifies an HP printer"
            )
            vendorId == SAMSUNG_VID || manufacturer.contains("SAMSUNG") ||
                manufacturer.contains("XEROX") && modelUpper.contains("PHASER") -> Route(
                Family.SPLIX, DriverPackager.DRV_SPLIX, model.takeIf { it.isNotBlank() },
                "IEEE-1284 identifies a Samsung/Xerox SPLIX-family printer"
            )
            model.isNotBlank() -> Route(
                Family.GUTENPRINT, DriverPackager.DRV_GUTENPRINT, model,
                "Model is present but no manufacturer-specific route is available"
            )
            else -> Route(Family.UNSUPPORTED, null, null, "Printer did not return an IEEE-1284 model")
        }
    }

    private fun epsonModelId(model: String): String? =
        Regex("\\b(?:ET|L|XP|WF)-?\\d{3,5}\\b", RegexOption.IGNORE_CASE)
            .find(model)
            ?.value
            ?.replace("-", "")
            ?.uppercase(Locale.US)

    private const val EPSON_VID = 0x04B8
    private const val HP_VID = 0x03F0
    private const val SAMSUNG_VID = 0x04E8
}
