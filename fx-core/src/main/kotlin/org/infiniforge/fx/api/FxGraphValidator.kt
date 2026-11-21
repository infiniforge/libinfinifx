package org.infiniforge.fx.api

object FxGraphValidator {
    fun validate(graph: FxGraph, descriptors: Map<String, FxEffectDescriptor>): FxExecutionPlan {
        val errors = mutableListOf<FxDiagnostic>()
        fun error(code: String, message: String, nodeId: String? = null) { errors += FxDiagnostic(code, message, nodeId) }
        if (graph.nodes.size > FxGraph.MAX_NODES) error("node-limit", "Graph exceeds ${FxGraph.MAX_NODES} nodes")
        if (graph.connections.size > FxGraph.MAX_CONNECTIONS) error("connection-limit", "Graph exceeds ${FxGraph.MAX_CONNECTIONS} connections")

        val byId = linkedMapOf<String, FxNode>()
        graph.nodes.forEach { node ->
            if (node.id == FxGraph.SOURCE_NODE_ID || !node.id.matches(Regex("[A-Za-z0-9_.-]{1,64}"))) {
                error("invalid-node-id", "Invalid or reserved node id '${node.id}'", node.id)
            } else if (byId.put(node.id, node) != null) {
                error("duplicate-node", "Duplicate node '${node.id}'", node.id)
            }
        }

        val incoming = graph.nodes.associate { it.id to mutableMapOf<String, String>() }
        val adjacency = graph.nodes.associate { it.id to mutableListOf<String>() }
        val consumers = mutableMapOf<String, Int>()
        graph.connections.forEach { edge ->
            val destination = byId[edge.toNodeId]
            if (destination == null) {
                error("missing-destination", "Connection target '${edge.toNodeId}' does not exist", edge.toNodeId)
                return@forEach
            }
            if (edge.fromNodeId != FxGraph.SOURCE_NODE_ID && edge.fromNodeId !in byId) {
                error("missing-source-node", "Connection source '${edge.fromNodeId}' does not exist", destination.id)
                return@forEach
            }
            val descriptor = descriptors[destination.effectId]
            if (descriptor == null) {
                error("unknown-effect", "Effect '${destination.effectId}' is not registered", destination.id)
                return@forEach
            }
            if (descriptor.inputPorts.none { it.name == edge.inputPort }) {
                error("unknown-input", "Effect '${descriptor.id}' has no input '${edge.inputPort}'", destination.id)
                return@forEach
            }
            val previous = incoming.getValue(destination.id).put(edge.inputPort, edge.fromNodeId)
            if (previous != null) error("duplicate-input", "Input '${edge.inputPort}' is connected more than once", destination.id)
            if (edge.fromNodeId != FxGraph.SOURCE_NODE_ID) adjacency.getValue(edge.fromNodeId).add(destination.id)
            consumers[edge.fromNodeId] = (consumers[edge.fromNodeId] ?: 0) + 1
        }

        graph.nodes.forEach { node ->
            val descriptor = descriptors[node.effectId]
            if (descriptor == null) {
                error("unknown-effect", "Effect '${node.effectId}' is not registered", node.id)
                return@forEach
            }
            val connected = incoming[node.id].orEmpty()
            descriptor.inputPorts.filter { it.required && it.name !in connected }.forEach {
                error("missing-input", "Effect '${descriptor.id}' requires input '${it.name}'", node.id)
            }
            val schema = descriptor.parameters.associateBy { it.name }
            node.parameters.forEach { (name, value) ->
                val parameter = schema[name]
                if (parameter == null) error("unknown-parameter", "Unknown parameter '$name' for '${descriptor.id}'", node.id)
                else if (!validValue(parameter, value)) error("invalid-parameter", "Invalid value for '${descriptor.id}.$name'", node.id)
            }
        }

        if (graph.outputNodeId != FxGraph.SOURCE_NODE_ID && graph.outputNodeId !in byId) {
            error("missing-output", "Output node '${graph.outputNodeId}' does not exist")
        }
        if (graph.outputNodeId != FxGraph.SOURCE_NODE_ID && adjacency[graph.outputNodeId].orEmpty().isNotEmpty()) {
            error("output-not-sink", "Output node '${graph.outputNodeId}' also feeds another node", graph.outputNodeId)
        }
        if (graph.outputNodeId == FxGraph.SOURCE_NODE_ID && graph.nodes.isNotEmpty()) {
            error("unused-nodes", "A source-only output cannot contain effect nodes")
        }

        if (graph.outputNodeId != FxGraph.SOURCE_NODE_ID && graph.outputNodeId in byId) {
            val reachable = mutableSetOf<String>()
            val pending = ArrayDeque<String>()
            pending.add(graph.outputNodeId)
            while (pending.isNotEmpty()) {
                val current = pending.removeFirst()
                if (!reachable.add(current)) continue
                incoming[current].orEmpty().values.filter { it != FxGraph.SOURCE_NODE_ID }.forEach(pending::addLast)
            }
            graph.nodes.filter { it.id !in reachable }.forEach {
                error("unreachable-node", "Node '${it.id}' does not contribute to output '${graph.outputNodeId}'", it.id)
            }
        }
        consumers[graph.outputNodeId] = (consumers[graph.outputNodeId] ?: 0) + 1

        val indegree = graph.nodes.associate { it.id to 0 }.toMutableMap()
        adjacency.forEach { (_, targets) -> targets.forEach { to -> indegree[to] = indegree.getValue(to) + 1 } }
        val ready = ArrayDeque(graph.nodes.filter { indegree[it.id] == 0 }.map { it.id })
        val orderedIds = mutableListOf<String>()
        while (ready.isNotEmpty()) {
            val current = ready.removeFirst()
            orderedIds += current
            adjacency[current].orEmpty().forEach { target ->
                val remaining = indegree.getValue(target) - 1
                indegree[target] = remaining
                if (remaining == 0) ready.addLast(target)
            }
        }
        if (orderedIds.size != graph.nodes.size) error("cycle", "Graph contains a cycle")

        if (errors.isNotEmpty()) throw FxGraphValidationException(errors)
        return FxExecutionPlan(orderedIds.map(byId::getValue), consumers.toMap())
    }

    /** Completes omitted values from descriptor defaults and rejects type/range mismatches. */
    fun validateParameters(descriptor: FxEffectDescriptor, values: Map<String, FxParameterValue>): Map<String, FxParameterValue> {
        val schema = descriptor.parameters.associateBy { it.name }
        val errors = values.mapNotNull { (name, value) ->
            val parameter = schema[name]
            when {
                parameter == null -> FxDiagnostic("unknown-parameter", "Unknown parameter '${descriptor.id}.$name'")
                !validValue(parameter, value) -> FxDiagnostic("invalid-parameter", "Invalid value for '${descriptor.id}.$name'")
                else -> null
            }
        }
        if (errors.isNotEmpty()) throw FxGraphValidationException(errors)
        return descriptor.parameters.associate { it.name to (values[it.name] ?: it.defaultValue) }
    }

    private fun validValue(parameter: FxParameterDescriptor, value: FxParameterValue): Boolean = when (parameter.type) {
        FxParameterType.FLOAT -> (value as? FxParameterValue.FloatValue)?.value?.let {
            it.isFinite() && (parameter.minimum == null || it >= parameter.minimum) &&
                (parameter.maximum == null || it <= parameter.maximum)
        } ?: false
        FxParameterType.INT -> (value as? FxParameterValue.IntValue)?.value?.toFloat()?.let {
            (parameter.minimum == null || it >= parameter.minimum) && (parameter.maximum == null || it <= parameter.maximum)
        } ?: false
        FxParameterType.BOOLEAN -> value is FxParameterValue.BoolValue
        FxParameterType.ENUM -> (value as? FxParameterValue.EnumValue)?.value in parameter.enumValues
        FxParameterType.VECTOR -> (value as? FxParameterValue.VectorValue)?.values?.let { values ->
            values.isNotEmpty() && values.size <= 4 && values.all { it.isFinite() }
        } ?: false
    }
}
