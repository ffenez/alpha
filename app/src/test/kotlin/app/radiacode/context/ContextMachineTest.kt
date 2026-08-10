package app.radiacode.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Transition table of the Wi-Fi context machine (spec §3.4). Every state is
 * entered and left through real events; the grace period is exercised on both
 * of its outcomes.
 */
class ContextMachineTest {

    private val home = "hash-home"
    private val office = "hash-office"
    private val foreign = "hash-cafe"
    private val bindings = mapOf(home to 1L, office to 2L)
    private val config = ContextConfig(graceMillis = 180_000L)

    private fun reduce(
        state: MeasurementContext,
        event: ContextEvent,
        bindings: Map<String, Long> = this.bindings,
    ) = ContextMachine.reduce(state, event, bindings, config)

    @Test
    fun `known network activates its profile immediately`() {
        val state = reduce(MeasurementContext.NoContext, ContextEvent.Network(home, 0))
        assertEquals(MeasurementContext.AutoKnown(1L), state)
        assertTrue(state.isReliable)
    }

    @Test
    fun `switching between two known networks needs no grace period`() {
        var state: MeasurementContext = MeasurementContext.AutoKnown(1L)
        state = reduce(state, ContextEvent.Network(office, 1_000))
        assertEquals(MeasurementContext.AutoKnown(2L), state)
    }

    @Test
    fun `losing the known network freezes the baseline for the grace period`() {
        var state: MeasurementContext = MeasurementContext.AutoKnown(1L)
        state = reduce(state, ContextEvent.Network(null, 10_000))
        assertEquals(
            MeasurementContext.AutoUncertain(1L, 10_000, MeasurementContext.Pending.TRANSIT),
            state,
        )
        assertFalse(state.isReliable, "baseline must be frozen while uncertain")
        assertEquals(1L, ContextMachine.activeProfileId(state, 9L, 8L))

        state = reduce(state, ContextEvent.Tick(10_000 + 179_000))
        assertTrue(state is MeasurementContext.AutoUncertain, "grace not over yet")

        state = reduce(state, ContextEvent.Tick(10_000 + 180_000))
        assertEquals(MeasurementContext.AutoTransit, state)
        assertTrue(state.isReliable)
        assertEquals(9L, ContextMachine.activeProfileId(state, 9L, 8L))
    }

    @Test
    fun `unknown network resolves to no context after the grace period`() {
        var state: MeasurementContext = MeasurementContext.AutoKnown(1L)
        state = reduce(state, ContextEvent.Network(foreign, 0))
        assertEquals(
            MeasurementContext.AutoUncertain(1L, 0, MeasurementContext.Pending.NO_CONTEXT),
            state,
        )
        state = reduce(state, ContextEvent.Tick(config.graceMillis))
        assertEquals(MeasurementContext.NoContext, state)
        assertEquals(8L, ContextMachine.activeProfileId(state, 9L, 8L))
    }

    @Test
    fun `changing the pending outcome does not restart the grace countdown`() {
        var state: MeasurementContext = MeasurementContext.AutoKnown(1L)
        state = reduce(state, ContextEvent.Network(null, 0))
        state = reduce(state, ContextEvent.Network(foreign, 120_000))
        assertEquals(
            MeasurementContext.AutoUncertain(1L, 0, MeasurementContext.Pending.NO_CONTEXT),
            state,
            "grace is measured from losing the known network",
        )
        state = reduce(state, ContextEvent.Tick(config.graceMillis))
        assertEquals(MeasurementContext.NoContext, state)
    }

    @Test
    fun `known network returning during the grace period restores the profile`() {
        var state: MeasurementContext = MeasurementContext.AutoKnown(1L)
        state = reduce(state, ContextEvent.Network(null, 0))
        state = reduce(state, ContextEvent.Network(home, 60_000))
        assertEquals(MeasurementContext.AutoKnown(1L), state)
    }

    @Test
    fun `manual choice sticks through every network event`() {
        var state: MeasurementContext = reduce(
            MeasurementContext.AutoKnown(1L),
            ContextEvent.SelectManually(5L),
        )
        assertEquals(MeasurementContext.Manual(5L), state)
        assertTrue(state.isManual)

        state = reduce(state, ContextEvent.Network(office, 1_000))
        assertEquals(MeasurementContext.Manual(5L), state, "Wi-Fi must not override a manual pick")
        state = reduce(state, ContextEvent.Network(null, 2_000))
        assertEquals(MeasurementContext.Manual(5L), state)
        state = reduce(state, ContextEvent.Tick(999_000))
        assertEquals(MeasurementContext.Manual(5L), state)
    }

    @Test
    fun `return to auto resolves at once without a grace period`() {
        val manual = MeasurementContext.Manual(5L)
        assertEquals(
            MeasurementContext.AutoKnown(2L),
            reduce(manual, ContextEvent.ReturnToAuto(office, 0)),
        )
        assertEquals(
            MeasurementContext.AutoTransit,
            reduce(manual, ContextEvent.ReturnToAuto(null, 0)),
        )
        assertEquals(
            MeasurementContext.NoContext,
            reduce(manual, ContextEvent.ReturnToAuto(foreign, 0)),
        )
    }

    @Test
    fun `binding to a profile that stopped auto-activating is ignored`() {
        // ProfileTree.autoBindings filters those out; the machine then simply
        // sees an unknown network.
        val state = reduce(
            MeasurementContext.NoContext,
            ContextEvent.Network(home, 0),
            bindings = emptyMap(),
        )
        assertEquals(MeasurementContext.NoContext, state)
    }

    @Test
    fun `no known profile means no grace period at all`() {
        val state = reduce(MeasurementContext.AutoTransit, ContextEvent.Network(foreign, 0))
        assertEquals(MeasurementContext.NoContext, state)
    }

    @Test
    fun `missing special profiles fall back to no profile instead of guessing`() {
        assertEquals(null, ContextMachine.activeProfileId(MeasurementContext.AutoTransit, null, 8L))
        assertEquals(null, ContextMachine.activeProfileId(MeasurementContext.NoContext, 9L, null))
    }
}
