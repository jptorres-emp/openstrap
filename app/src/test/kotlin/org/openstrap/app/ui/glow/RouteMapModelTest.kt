package org.openstrap.app.ui.glow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMapModelTest {
    @Test fun `opening is per workout and resets the choices`() {
        val m = RouteMapModel()
        m.routeOnly = true
        m.selected = 3
        m.open(10L)
        assertTrue(m.isOpen(10L))
        assertFalse(m.isOpen(11L))
        assertFalse(m.routeOnly)
        assertNull(m.selected)
    }

    @Test fun `closing closes for every workout and keeps nothing open`() {
        val m = RouteMapModel()
        m.open(10L)
        m.close()
        assertFalse(m.isOpen(10L))
        assertEquals(null, m.openFor)
    }

    @Test fun `picking the picked kilometre clears it`() {
        val m = RouteMapModel()
        m.pick(2)
        assertEquals(2, m.selected)
        m.pick(4)
        assertEquals(4, m.selected)
        m.pick(4)
        assertNull(m.selected)
    }
}
