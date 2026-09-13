package com.cprint.app.driver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterDriverRouterTest {
    @Test fun `parses IEEE 1284 fields and commands`() {
        val identity = PrinterDriverRouter.parseDeviceId(
            "MFG:EPSON;MDL:L3118 Series;CMD:ESCPL2,BDC,D4;"
        )
        assertEquals("EPSON", identity.manufacturer)
        assertEquals("L3118 Series", identity.model)
        assertTrue("ESCPL2" in identity.commands)
    }

    @Test fun `routes Epson model to escpr`() {
        val route = PrinterDriverRouter.route(
            0x04B8,
            PrinterDriverRouter.parseDeviceId("MFG:EPSON;MDL:L3118 Series;")
        )
        assertEquals(PrinterDriverRouter.Family.ESCPR, route.family)
        assertEquals("L3118", route.modelId)
    }

    @Test fun `routes HP identity to hplip`() {
        val route = PrinterDriverRouter.route(
            0x03F0,
            PrinterDriverRouter.parseDeviceId("MFG:Hewlett-Packard;MDL:HP LaserJet Pro M404;")
        )
        assertEquals(PrinterDriverRouter.Family.HPLIP, route.family)
    }
}
