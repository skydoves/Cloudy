/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.graphics

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayerV23
import androidx.compose.ui.graphics.layer.GraphicsLayerV29
import androidx.compose.ui.graphics.layer.GraphicsViewLayer
import androidx.compose.ui.graphics.layer.view.DrawChildContainer
import androidx.compose.ui.graphics.layer.view.ViewLayerContainer
import androidx.compose.ui.graphics.shadow.ShadowContext

/**
 * Create a new [GraphicsContext] with the provided [ViewGroup] to contain [View] based layers.
 *
 * @param layerContainer [ViewGroup] used to contain [View] based layers that are created by the
 *   returned [GraphicsContext]
 */
/**
     * Creates a GraphicsContext that hosts platform graphics layers in the provided ViewGroup.
     *
     * @param layerContainer The ViewGroup used to host View-based graphics layers and manage their lifecycle.
     * @return A GraphicsContext associated with the given ViewGroup.
     */
    fun GraphicsContext(layerContainer: ViewGroup): GraphicsContext =
    AndroidGraphicsContext(layerContainer)

private class AndroidGraphicsContext(private val ownerView: ViewGroup) : GraphicsContext {

    private val lock = Any()
    private var viewLayerContainer: DrawChildContainer? = null
    private var componentCallbackRegistered = false
    private var shadowCache: ShadowContext? = null

    private val componentCallback: ComponentCallbacks2

    init {
        componentCallback =
            object : ComponentCallbacks2 {
                /**
                 * Handle device configuration changes; no action is performed.
                 *
                 * @param newConfig The new device configuration.
                 */
                override fun onConfigurationChanged(newConfig: Configuration) {
                    // NO-OP
                }

                /**
                 * No-op implementation of the low-memory callback; intentionally left empty to satisfy the
                 * ComponentCallbacks2 interface without performing any action.
                 */
                @Suppress("OVERRIDE_DEPRECATION") // b/407491706
                override fun onLowMemory() {
                    // NO-OP
                }

                /**
                 * Clears the cached shadow context when the system requests memory trimming at or beyond background level.
                 *
                 * @param level The memory trim level; if `level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND` the shadow cache is cleared.
                 */
                override fun onTrimMemory(level: Int) {
                    // See CacheManager.cpp. HWUI releases graphics resources whenever the trim
                    // memory callback exceed the level of TRIM_MEMORY_BACKGROUND so do the same
                    // here to release shadow dependencies
                    if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                        clearShadowCache()
                    }
                }
            }
        if (ownerView.isAttachedToWindow) {
            registerComponentCallback(ownerView.context)
        }
        ownerView.addOnAttachStateChangeListener(
            object : OnAttachStateChangeListener {
                /**
                 * Handles the view being attached to a window by registering the component callback
                 * with the view's context.
                 *
                 * @param v The view that was attached to the window.
                 */
                override fun onViewAttachedToWindow(v: View) {
                    // If the View is attached to the window again, re-add the component
                    // callbacks
                    registerComponentCallback(v.context)
                }

                /**
                 * Handles a view being detached by unregistering the component callbacks and clearing the shadow cache.
                 *
                 * @param v The view that was detached from the window.
                 */
                override fun onViewDetachedFromWindow(v: View) {
                    // When the View is detached from the window, remove the component callbacks
                    // used to listen to trim memory signals
                    unregisterComponentCallback(v.context)
                    clearShadowCache()
                }
            }
        )
    }

    /**
     * Clears the cached ShadowContext if present and removes the cache reference.
     */
    private fun clearShadowCache() {
        shadowCache?.clearCache()
        shadowCache = null
    }

    /**
     * Registers the internal ComponentCallbacks2 instance with the application's context if it is not already registered.
     *
     * This will bind the callback to the application context and mark it as registered to avoid duplicate registrations.
     */
    private fun registerComponentCallback(context: Context) {
        if (!componentCallbackRegistered) {
            context.applicationContext.registerComponentCallbacks(componentCallback)
            componentCallbackRegistered = true
        }
    }

    /**
     * Unregisters the component callback from the application's lifecycle if it is currently registered.
     *
     * @param context The context whose applicationContext will be used to unregister the callback.
     */
    private fun unregisterComponentCallback(context: Context) {
        if (componentCallbackRegistered) {
            context.applicationContext.unregisterComponentCallbacks(componentCallback)
            componentCallbackRegistered = false
        }
    }

    /**
     * Creates and returns a GraphicsLayer suitable for the current Android runtime.
     *
     * The returned GraphicsLayer is backed by a RenderNode-based implementation when supported
     * (API 29+ or RenderNode-compatible API 23+), and falls back to a View-based layer when
     * RenderNode support is unavailable or creation fails.
     *
     * @return A new GraphicsLayer configured for the device's available graphics layer mechanism. 
     */
    override fun createGraphicsLayer(): GraphicsLayer {
        synchronized(lock) {
            val ownerId = getUniqueDrawingId(ownerView)
            val layerImpl =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    GraphicsLayerV29(ownerId)
                } else if (
                    isRenderNodeCompatible && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ) {
                    try {
                        GraphicsLayerV23(ownerView, ownerId)
                    } catch (_: Throwable) {
                        // If we ever failed to create an instance of the RenderNode stub
                        // based
                        // GraphicsLayer, always fallback to creation of View based layers
                        // as it is
                        // unlikely that subsequent attempts to create a GraphicsLayer with
                        // RenderNode
                        // stubs would be successful.
                        isRenderNodeCompatible = false
                        GraphicsViewLayer(obtainViewLayerContainer(ownerView), ownerId)
                    }
                } else {
                    GraphicsViewLayer(obtainViewLayerContainer(ownerView), ownerId)
                }
            val layer = GraphicsLayer(layerImpl)
            return layer
        }
    }

    override val shadowContext: ShadowContext
        get() = shadowCache ?: ShadowContext().also { shadowCache = it }

    /**
     * Releases the given graphics layer and frees any resources associated with it.
     *
     * @param layer The graphics layer to release.
     */
    override fun releaseGraphicsLayer(layer: GraphicsLayer) {
        synchronized(lock) { layer.release() }
    }

    /**
     * Obtain the DrawChildContainer attached to the given ViewGroup, creating and adding one if none exists.
     *
     * This function caches the container on first creation; calling it will add a new ViewLayerContainer to
     * the provided ownerView when a cached instance is not present.
     *
     * @param ownerView The ViewGroup that will host (or already hosts) the DrawChildContainer.
     * @return The existing or newly created DrawChildContainer attached to the provided ownerView.
     */
    private fun obtainViewLayerContainer(ownerView: ViewGroup): DrawChildContainer {
        var container = viewLayerContainer
        if (container == null) {
            val context = ownerView.context

            container = ViewLayerContainer(context)
            ownerView.addView(container)
            viewLayerContainer = container
        }
        return container
    }

    /**
         * Obtains the unique drawing ID for the given view on Android Q (API 29) and newer.
         *
         * @param view The view whose unique drawing ID should be retrieved.
         * @return The view's unique drawing ID on API 29 and above, or `-1` on older Android versions.
         */
        private fun getUniqueDrawingId(view: View): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UniqueDrawingIdApi29.getUniqueDrawingId(view)
        } else {
            -1
        }

    internal companion object {
        var isRenderNodeCompatible: Boolean = true
    }

    @RequiresApi(29)
    private object UniqueDrawingIdApi29 {
        /**
 * Obtain the view's unique drawing identifier.
 *
 * @return The `Long` unique drawing id associated with the given view.
 */
@JvmStatic fun getUniqueDrawingId(view: View) = view.uniqueDrawingId
    }
}