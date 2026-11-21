package org.infiniforge.fx.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LightningSpectrumTest {
    @Test fun generatorPresetIsFullWidthAudioAwareAndValidWithoutAudio() {
        val descriptor = BuiltinFx.byId.getValue("generator:lightning-spectrum")
        assertEquals(FxEffectCategory.GENERATOR, descriptor.category)
        assertTrue(descriptor.inputPorts.isEmpty())
        assertTrue("audio.bands" in descriptor.metadataInterests)
        assertTrue(descriptor.parameters.any { it.name == "maxHeight" })

        val preset = BuiltinPresets.all.getValue("lightning-spectrum")
        val plan = FxGraphValidator.validate(preset.graph, BuiltinFx.byId)
        assertEquals(listOf("spectrum"), plan.orderedNodes.map { it.id })
        assertEquals(preset.graph.outputNodeId, plan.orderedNodes.single().id)
    }
}
