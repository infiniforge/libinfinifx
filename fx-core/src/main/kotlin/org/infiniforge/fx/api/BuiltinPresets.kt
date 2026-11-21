package org.infiniforge.fx.api

object BuiltinPresets {
    private fun chain(name: String, effect: String): FxGraph = FxGraph(
        name = name, nodes = listOf(FxNode("effect", effect)),
        connections = listOf(FxConnection(FxGraph.SOURCE_NODE_ID, "effect", "image")),
        outputNodeId = "effect",
    )

    val all: Map<String, FxPreset> = listOf(
        FxPreset("neutral-pass-through", "Exact sampled copy with no effect nodes; source-only bypass.",
            FxGraph("neutral-pass-through", emptyList(), emptyList())),
        FxPreset("crt-classic", "Stylized classic CRT scanlines and shadow mask.", chain("crt-classic", "display:crt")),
        FxPreset("lcd-gameboy-dmg", "Four-shade green LCD remap over logical source pixels.", chain("lcd-gameboy-dmg", "display:lcd")),
        FxPreset("water-over-image", "Autonomous calm-to-choppy procedural water refraction.", chain("water-over-image", "distort:water-refraction")),
        FxPreset("heat-haze", "Private evolving heat field refracts the input image.", chain("heat-haze", "distort:heat-ripple")),
        FxPreset(
            "audio-fire-overlay", "Independent transparent fire generator composited over the input image.",
            FxGraph(
                "audio-fire-overlay",
                listOf(FxNode("flame", "generator:fire"), FxNode("over", "composite:blend")),
                listOf(FxConnection("source", "over", "base"), FxConnection("flame", "over", "overlay")),
                "over",
            ),
        ),
    ).associateBy { it.name }
}
