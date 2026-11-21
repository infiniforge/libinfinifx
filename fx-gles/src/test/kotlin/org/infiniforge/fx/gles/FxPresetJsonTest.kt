package org.infiniforge.fx.gles

import org.infiniforge.fx.api.BuiltinFx
import org.infiniforge.fx.api.BuiltinPresets
import org.infiniforge.fx.api.FxGraphValidator
import org.infiniforge.fx.api.FxParameterValue
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FxPresetJsonTest {
    @Test fun builtInPresetsRoundTripAndValidate() {
        BuiltinPresets.all.values.forEach { preset ->
            val decoded = FxPresetJson.decode(FxPresetJson.encode(preset))
            assertEquals(preset.name, decoded.name)
            assertEquals(preset.graph, decoded.graph)
            assertEquals(preset.schemaVersion, decoded.schemaVersion)
            FxGraphValidator.validate(decoded.graph, BuiltinFx.byId)
        }
    }

    @Test fun unsupportedSchemaAndUnknownFieldsAreRejected() {
        val json = FxPresetJson.encode(BuiltinPresets.all.getValue("crt-classic"))
        val unsupported = JSONObject(json).put("schemaVersion", 2).toString()
        assertFailsWith<IllegalArgumentException> { FxPresetJson.decode(unsupported) }

        val unknown = JSONObject(json).put("surprise", true).toString()
        val error = assertFailsWith<IllegalArgumentException> { FxPresetJson.decode(unknown) }
        assertTrue(error.message.orEmpty().contains("unsupported field"))
    }

    @Test fun malformedParameterTypesAreNotCoerced() {
        val source = BuiltinPresets.all.getValue("crt-classic")
        val graph = source.graph.copy(nodes = source.graph.nodes.map { node ->
            node.copy(parameters = mapOf("scanlineStrength" to FxParameterValue.FloatValue(.28f)))
        })
        val json = FxPresetJson.encode(source.copy(graph = graph))
        val root = JSONObject(json)
        val nodes = root.getJSONObject("graph").getJSONArray("nodes")
        val params = nodes.getJSONObject(0).getJSONObject("parameters")
        params.getJSONObject("scanlineStrength").put("value", "0.4")
        val error = assertFailsWith<IllegalArgumentException> { FxPresetJson.decode(root.toString()) }
        assertTrue(error.message.orEmpty().contains("numeric"))
    }
}
