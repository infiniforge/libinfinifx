package org.infiniforge.fx.gles

import org.infiniforge.fx.api.*
import org.json.JSONArray
import org.json.JSONObject

/** Bounded JSON codec for schema version 1 presets. Hardware names/paths are never serialized. */
object FxPresetJson {
    const val SCHEMA_VERSION = 1
    const val MAX_JSON_CHARS = 256 * 1024

    fun encode(preset: FxPreset): String {
        require(preset.schemaVersion == SCHEMA_VERSION) { "Unsupported preset schema version ${preset.schemaVersion}" }
        val graph = preset.graph
        require(graph.nodes.size <= FxGraph.MAX_NODES) { "Preset contains too many nodes" }
        require(graph.connections.size <= FxGraph.MAX_CONNECTIONS) { "Preset contains too many connections" }
        val root = JSONObject()
            .put("schemaVersion", preset.schemaVersion)
            .put("name", preset.name)
            .put("description", preset.description)
            .put("graph", JSONObject()
                .put("name", graph.name)
                .put("outputNodeId", graph.outputNodeId)
                .put("nodes", JSONArray().also { array ->
                    graph.nodes.forEach { node ->
                        val params = JSONObject()
                        node.parameters.forEach { (key, value) -> params.put(key, encodeValue(value)) }
                        array.put(JSONObject().put("id", node.id).put("effectId", node.effectId)
                            .put("enabled", node.enabled).put("parameters", params))
                    }
                })
                .put("connections", JSONArray().also { array ->
                    graph.connections.forEach { edge ->
                        array.put(JSONObject().put("from", edge.fromNodeId).put("to", edge.toNodeId).put("input", edge.inputPort))
                    }
                }))
        val result = root.toString()
        require(result.length <= MAX_JSON_CHARS) { "Preset exceeds ${MAX_JSON_CHARS} characters" }
        return result
    }

    fun decode(json: String): FxPreset {
        require(json.length <= MAX_JSON_CHARS) { "Preset exceeds ${MAX_JSON_CHARS} characters" }
        val root = JSONObject(json)
        requireKeys(root, "preset", required = setOf("schemaVersion", "name", "graph"), optional = setOf("description"))
        val schemaNumber = root.get("schemaVersion") as? Number
            ?: throw IllegalArgumentException("preset.schemaVersion must be an integer")
        val schemaValue = schemaNumber.toDouble()
        require(schemaValue.isFinite() && schemaValue % 1.0 == 0.0 && schemaValue in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
            "preset.schemaVersion must be an integer"
        }
        val schema = schemaValue.toInt()
        require(schema == SCHEMA_VERSION) { "Unsupported preset schema version $schema (supported: $SCHEMA_VERSION)" }
        val graphJson = root.getJSONObject("graph")
        requireKeys(graphJson, "graph", required = setOf("nodes", "connections"), optional = setOf("name", "outputNodeId"))
        val nodeArray = graphJson.getJSONArray("nodes")
        val edgeArray = graphJson.getJSONArray("connections")
        require(nodeArray.length() <= FxGraph.MAX_NODES) { "Preset contains too many nodes" }
        require(edgeArray.length() <= FxGraph.MAX_CONNECTIONS) { "Preset contains too many connections" }
        val nodes = buildList {
            for (i in 0 until nodeArray.length()) {
                val item = nodeArray.getJSONObject(i)
                val location = "node[$i]"
                requireKeys(item, location, required = setOf("id", "effectId"), optional = setOf("enabled", "parameters"))
                val paramsObject = if (item.has("parameters")) item.getJSONObject("parameters") else JSONObject()
                val params = buildMap {
                    val names = paramsObject.keys()
                    while (names.hasNext()) {
                        val key = names.next()
                        put(key, decodeValue(paramsObject.getJSONObject(key), "node[$i].parameters.$key"))
                    }
                }
                add(FxNode(
                    string(item, "id", location),
                    string(item, "effectId", location),
                    params,
                    optionalBoolean(item, "enabled", true, location),
                ))
            }
        }
        val connections = buildList {
            for (i in 0 until edgeArray.length()) {
                val item = edgeArray.getJSONObject(i)
                requireKeys(item, "connection[$i]", required = setOf("from", "to", "input"))
                add(FxConnection(
                    string(item, "from", "connection[$i]"),
                    string(item, "to", "connection[$i]"),
                    string(item, "input", "connection[$i]"),
                ))
            }
        }
        val presetName = string(root, "name", "preset")
        return FxPreset(
            name = presetName,
            description = optionalString(root, "description", "", "preset"),
            schemaVersion = schema,
            graph = FxGraph(
                optionalString(graphJson, "name", presetName, "graph"),
                nodes,
                connections,
                optionalString(graphJson, "outputNodeId", FxGraph.SOURCE_NODE_ID, "graph"),
            ),
        )
    }

    private fun encodeValue(value: FxParameterValue): JSONObject = when (value) {
        is FxParameterValue.FloatValue -> JSONObject().put("type", "float").put("value", value.value.toDouble())
        is FxParameterValue.IntValue -> JSONObject().put("type", "int").put("value", value.value)
        is FxParameterValue.BoolValue -> JSONObject().put("type", "boolean").put("value", value.value)
        is FxParameterValue.EnumValue -> JSONObject().put("type", "enum").put("value", value.value)
        is FxParameterValue.VectorValue -> JSONObject().put("type", "vector").put("value", JSONArray(value.values))
    }

    private fun decodeValue(value: JSONObject, location: String): FxParameterValue {
        requireKeys(value, location, required = setOf("type", "value"))
        return when (string(value, "type", location)) {
        "float" -> {
            val number = valueNumber(value, location).toDouble()
            val result = number.toFloat()
            require(number.isFinite() && result.isFinite()) { "$location must be a finite float" }
            FxParameterValue.FloatValue(result)
        }
        "int" -> {
            val number = valueNumber(value, location).toDouble()
            require(number.isFinite() && number % 1.0 == 0.0 && number in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
                "$location must be a 32-bit integer"
            }
            FxParameterValue.IntValue(number.toInt())
        }
        "boolean" -> {
            val raw = value.get("value")
            require(raw is Boolean) { "$location must be a JSON boolean" }
            FxParameterValue.BoolValue(raw)
        }
        "enum" -> {
            val raw = value.get("value")
            require(raw is String) { "$location must be a JSON string" }
            FxParameterValue.EnumValue(raw)
        }
        "vector" -> {
            val array = value.getJSONArray("value")
            require(array.length() in 1..4) { "$location vector length must be 1..4" }
            FxParameterValue.VectorValue(List(array.length()) { index ->
                val number = array.get(index) as? Number
                    ?: throw IllegalArgumentException("$location[$index] must be numeric")
                val result = number.toDouble().toFloat()
                require(number.toDouble().isFinite() && result.isFinite()) { "$location[$index] must be finite" }
                result
            })
        }
        else -> throw IllegalArgumentException("Unknown parameter value type '${value.optString("type")}' at $location")
        }
    }

    private fun string(value: JSONObject, key: String, location: String): String {
        val raw = value.get(key)
        require(raw is String) { "$location.$key must be a JSON string" }
        return raw
    }

    private fun optionalString(value: JSONObject, key: String, default: String, location: String): String =
        if (value.has(key)) string(value, key, location) else default

    private fun optionalBoolean(value: JSONObject, key: String, default: Boolean, location: String): Boolean {
        if (!value.has(key)) return default
        val raw = value.get(key)
        require(raw is Boolean) { "$location.$key must be a JSON boolean" }
        return raw
    }

    private fun valueNumber(value: JSONObject, location: String): Number {
        val raw = value.get("value")
        require(raw is Number) { "$location must be numeric" }
        return raw
    }

    private fun requireKeys(
        value: JSONObject,
        location: String,
        required: Set<String>,
        optional: Set<String> = emptySet(),
    ) {
        required.forEach { key -> require(value.has(key)) { "$location is missing required field '$key'" } }
        val allowed = required + optional
        val unknown = value.keys().asSequence().filterNot(allowed::contains).sorted().toList()
        require(unknown.isEmpty()) { "$location contains unsupported field(s): ${unknown.joinToString()}" }
    }
}
