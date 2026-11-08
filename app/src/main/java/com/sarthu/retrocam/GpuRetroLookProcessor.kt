package com.sarthu.retrocam

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max

/**
 * Off-screen OpenGL ES 2.0 photo renderer. Runs on the device GPU and falls back to
 * RetroLookProcessor if EGL/GLES is unavailable on a particular vendor build.
 * The renderer intentionally processes still images; it is not the live CameraX
 * preview/video encoder path.
 */
object GpuRetroLookProcessor {
    private data class Look(val saturation: Float, val contrast: Float, val warmth: Float,
                            val grain: Float, val vignette: Float, val fade: Float,
                            val scanlines: Float, val chroma: Float)

    private fun look(name: String): Look = when (name.uppercase()) {
        "VHS" -> Look(.78f, 1.12f, -.04f, .075f, .18f, .035f, .22f, .0035f)
        "DV" -> Look(.92f, 1.08f, -.02f, .025f, .12f, .015f, .08f, .0015f)
        "SUPER 8", "8MM" -> Look(.82f, 1.16f, .16f, .105f, .38f, .075f, .02f, .001f)
        "16MM" -> Look(.90f, 1.10f, .09f, .065f, .27f, .045f, .01f, .0008f)
        "35MM" -> Look(1.08f, 1.06f, .055f, .035f, .16f, .018f, 0f, 0f)
        "CCD" -> Look(1.20f, 1.18f, -.025f, .018f, .10f, 0f, .04f, .001f)
        "PORTRA 400" -> Look(1.02f, 1.02f, .075f, .025f, .10f, .025f, 0f, 0f)
        "GOLD 200" -> Look(1.12f, 1.08f, .13f, .045f, .16f, .035f, 0f, 0f)
        "FADED" -> Look(.72f, .88f, .04f, .018f, .08f, .12f, 0f, 0f)
        "B&W", "BLACK & WHITE" -> Look(0f, 1.18f, 0f, .04f, .20f, .025f, 0f, 0f)
        else -> Look(1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f)
    }

    fun apply(source: Bitmap, lookName: String, intensity: Float = 1f): Bitmap {
        if (lookName.equals("ORIGINAL", true)) return source.copy(Bitmap.Config.ARGB_8888, false)
        var display: android.opengl.EGLDisplay? = null
        var context: android.opengl.EGLContext? = null
        var surface: android.opengl.EGLSurface? = null
        var texture = 0
        var program = 0
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY)
            val versions = IntArray(2)
            check(EGL14.eglInitialize(display, versions, 0, versions, 1))
            val configAttrs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE)
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, configAttrs, 0, configs, 0, 1, count, 0) && count[0] > 0)
            val contextAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttrs, 0)
            check(context != EGL14.EGL_NO_CONTEXT)
            val surfaceAttrs = intArrayOf(EGL14.EGL_WIDTH, source.width, EGL14.EGL_HEIGHT, source.height, EGL14.EGL_NONE)
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttrs, 0)
            check(surface != EGL14.EGL_NO_SURFACE)
            check(EGL14.eglMakeCurrent(display, surface, surface, context))

            program = createProgram(VERTEX, FRAGMENT)
            GLES20.glUseProgram(program)
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0); texture = textures[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, source, 0)

            val vertices = floatBuffer(floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,1f))
            val texCoords = floatBuffer(floatArrayOf(0f,1f, 1f,1f, 0f,0f, 1f,0f))
            val pos = GLES20.glGetAttribLocation(program, "aPosition")
            val uv = GLES20.glGetAttribLocation(program, "aTexCoord")
            vertices.position(0); GLES20.glEnableVertexAttribArray(pos)
            GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, vertices)
            texCoords.position(0); GLES20.glEnableVertexAttribArray(uv)
            GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 0, texCoords)
            GLES20.glViewport(0, 0, source.width, source.height)
            val p = look(lookName)
            uniform1i(program, "uTexture", 0)
            uniform1f(program, "uIntensity", intensity.coerceIn(0f, 1f))
            uniform1f(program, "uSaturation", p.saturation)
            uniform1f(program, "uContrast", p.contrast)
            uniform1f(program, "uWarmth", p.warmth)
            uniform1f(program, "uGrain", p.grain)
            uniform1f(program, "uVignette", p.vignette)
            uniform1f(program, "uFade", p.fade)
            uniform1f(program, "uScanlines", p.scanlines)
            uniform1f(program, "uChroma", p.chroma)
            uniform2f(program, "uResolution", source.width.toFloat(), source.height.toFloat())
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            val pixels = ByteBuffer.allocateDirect(source.width * source.height * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, source.width, source.height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
            val raw = IntArray(source.width * source.height)
            pixels.rewind()
            for (y in 0 until source.height) for (x in 0 until source.width) {
                val r = pixels.get().toInt() and 255; val g = pixels.get().toInt() and 255
                val b = pixels.get().toInt() and 255; val a = pixels.get().toInt() and 255
                raw[(source.height - 1 - y) * source.width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            return Bitmap.createBitmap(raw, source.width, source.height, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            return RetroLookProcessor.apply(source, lookName, intensity)
        } finally {
            if (display != null && display != EGL14.EGL_NO_DISPLAY) {
                // GL resources must be released while the EGL context is still current.
                try {
                    if (context != null && context != EGL14.EGL_NO_CONTEXT && surface != null && surface != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglMakeCurrent(display, surface, surface, context)
                        if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
                        if (program != 0) GLES20.glDeleteProgram(program)
                    }
                } catch (_: Exception) { }
                try { EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT) } catch (_: Exception) { }
                if (surface != null && surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != null && context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
            }
        }
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(values.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }
    private fun uniform1f(p: Int, n: String, v: Float) = GLES20.glUniform1f(GLES20.glGetUniformLocation(p, n), v)
    private fun uniform1i(p: Int, n: String, v: Int) = GLES20.glUniform1i(GLES20.glGetUniformLocation(p, n), v)
    private fun uniform2f(p: Int, n: String, x: Float, y: Float) = GLES20.glUniform2f(GLES20.glGetUniformLocation(p, n), x, y)

    private fun createProgram(vertex: String, fragment: String): Int {
        fun compile(type: Int, code: String): Int {
            val shader = GLES20.glCreateShader(type); GLES20.glShaderSource(shader, code); GLES20.glCompileShader(shader)
            val status = IntArray(1); GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(shader) }
            return shader
        }
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertex); val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        val p = GLES20.glCreateProgram(); GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs); GLES20.glLinkProgram(p)
        val status = IntArray(1); GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(p) }
        return p
    }

    private const val VERTEX = """
        attribute vec4 aPosition; attribute vec2 aTexCoord; varying vec2 vUv;
        void main(){ gl_Position=aPosition; vUv=aTexCoord; }
    """
    private const val FRAGMENT = """
        precision highp float;
        uniform sampler2D uTexture; uniform float uIntensity; uniform float uSaturation;
        uniform float uContrast; uniform float uWarmth; uniform float uGrain;
        uniform float uVignette; uniform float uFade; uniform float uScanlines;
        uniform float uChroma; uniform vec2 uResolution; varying vec2 vUv;
        float hash(vec2 p){ return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
        void main(){
          vec2 uv=vUv; vec2 centered=uv-0.5;
          float ca=uChroma*uIntensity;
          float r=texture2D(uTexture,uv+vec2(ca,0.0)).r;
          float g0=texture2D(uTexture,uv).g;
          float b=texture2D(uTexture,uv-vec2(ca,0.0)).b;
          vec3 c=vec3(r,g0,b); float gray=dot(c,vec3(0.299,0.587,0.114));
          c=mix(vec3(gray),c,mix(1.0,uSaturation,uIntensity));
          c=(c-0.5)*mix(1.0,uContrast,uIntensity)+0.5;
          c+=vec3(0.055,0.018,-0.045)*uWarmth*uIntensity;
          float vign=1.0-uVignette*uIntensity*clamp(dot(centered,centered)*2.0,0.0,1.0);
          float grain=(hash(floor(uv*uResolution)+vec2(17.0,31.0))-0.5)*uGrain*uIntensity;
          float scan=(mod(floor(uv.y*uResolution.y),3.0)<1.0)?(1.0-uScanlines*uIntensity):1.0;
          c=c*vign*scan+grain+uFade*uIntensity;
          gl_FragColor=vec4(clamp(c,0.0,1.0),1.0);
        }
    """
}
