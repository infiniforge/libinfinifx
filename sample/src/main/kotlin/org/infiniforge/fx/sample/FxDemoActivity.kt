package org.infiniforge.fx.sample

import android.app.Activity
import android.os.Bundle
import android.opengl.GLSurfaceView
import android.opengl.GLES30
import android.util.Log
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
import org.infiniforge.fx.api.*
import org.infiniforge.fx.gles.GlesFxEngine
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sin

class FxDemoActivity : Activity() {
    private lateinit var surface: GLSurfaceView
    private lateinit var renderer: DemoRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surface = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            preserveEGLContextOnPause = false
        }
        renderer = DemoRenderer()
        surface.setRenderer(renderer)
        surface.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 8, 12, 8)
        }
        val names = listOf(
            "neutral-pass-through", "crt-classic", "lcd-gameboy-dmg",
            "water-over-image", "heat-haze", "audio-fire-overlay",
        )
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                renderer.presetName = names[position]
            }
        }
        controls.addView(spinner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val audio = CheckBox(this).apply {
            text = "Synthetic audio"
            isChecked = true
            setOnCheckedChangeListener { _, checked -> renderer.syntheticAudio = checked }
        }
        controls.addView(audio)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(controls)
        root.addView(surface, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        surface.onResume()
    }

    override fun onPause() {
        surface.onPause()
        super.onPause()
    }

    private class DemoRenderer : GLSurfaceView.Renderer {
        @Volatile var presetName = "neutral-pass-through"
        @Volatile var syntheticAudio = true
        private var engine: GlesFxEngine? = null
        private var sourceTexture = 0
        private var width = 1
        private var height = 1
        private var frameIndex = 0L
        private var previousTime = 0L
        private var nextOnsetTime = 0L
        private var onsetId = 0L

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            // A new Renderer callback means the old EGL context may be gone; the driver owns its dead names.
            engine = GlesFxEngine()
            sourceTexture = makeAsymmetricFixture()
            previousTime = 0L
            nextOnsetTime = 0L
            GLES30.glClearColor(.035f, .045f, .06f, 1f)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            this.width = width.coerceAtLeast(1)
            this.height = height.coerceAtLeast(1)
            GLES30.glViewport(0, 0, this.width, this.height)
            engine?.resize(this.width, this.height)
        }

        override fun onDrawFrame(gl: GL10?) {
            val now = System.nanoTime()
            val delta = if (previousTime == 0L) 0f else ((now - previousTime) / 1_000_000_000.0).toFloat().coerceIn(0f, .1f)
            previousTime = now
            val event = if (syntheticAudio && now >= nextOnsetTime) {
                nextOnsetTime = now + 1_250_000_000L
                listOf(FxAudioEvent(onsetId++, now, .9f))
            } else emptyList()
            val audio = if (syntheticAudio) AudioFrameMetadata(
                timestampNanos = now,
                rms = (.25f + .2f * sin(now / 400_000_000.0).toFloat()).coerceIn(0f, 1f),
                peak = .75f,
                bands = List(16) { index -> (.2f + .15f * sin(now / 500_000_000.0 + index).toFloat()).coerceIn(0f, 1f) },
                lowEnergy = (.4f + .35f * sin(now / 300_000_000.0).toFloat()).coerceIn(0f, 1f),
                midEnergy = .3f,
                highEnergy = .25f,
                spectralFlux = if (event.isEmpty()) 0f else .9f,
                onsetEvents = event,
            ) else null
            val geometry = FxGeometry(
                sourceWidth = 320, sourceHeight = 180,
                destinationWidth = width, destinationHeight = height,
            )
            val frame = FxFrameInput(
                presentationTimeNanos = now,
                deltaTimeSeconds = delta,
                frameIndex = frameIndex++,
                geometry = geometry,
                sourceTexture = FxTexture(sourceTexture, 320, 180, sampling = FxSampling.NEAREST),
                audio = audio,
                randomSeed = 0x1F1F1L,
            )
            val graph = BuiltinPresets.all.getValue(presetName).graph
            when (val result = engine?.render(graph, frame, FxRenderTarget(0, width, height))) {
                is FxRenderResult.Failure -> Log.e("InfiniFX", "${result.code}: ${result.message}")
                else -> Unit
            }
        }

        private fun makeAsymmetricFixture(): Int {
            val w = 320
            val h = 180
            val bytes = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
            for (y in 0 until h) for (x in 0 until w) {
                val check = ((x / 16) + (y / 16)) % 2 == 0
                val color = when {
                    x < w / 2 && y >= h / 2 -> intArrayOf(232, 48, 32)
                    x >= w / 2 && y >= h / 2 -> intArrayOf(40, 210, 82)
                    x < w / 2 -> intArrayOf(44, 94, 232)
                    else -> intArrayOf(236, 212, 58)
                }
                val marker = (x < 52 && y < 28) || (x > w - 44 && y > h - 24)
                val scale = if (check || marker) 1f else .45f
                bytes.put(if (marker) 255.toByte() else (color[0] * scale).toInt().toByte())
                bytes.put(if (marker) 255.toByte() else (color[1] * scale).toInt().toByte())
                bytes.put(if (marker) 255.toByte() else (color[2] * scale).toInt().toByte())
                bytes.put(255.toByte())
            }
            bytes.position(0)
            val names = IntArray(1)
            GLES30.glGenTextures(1, names, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, names[0])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, bytes)
            return names[0]
        }
    }
}
