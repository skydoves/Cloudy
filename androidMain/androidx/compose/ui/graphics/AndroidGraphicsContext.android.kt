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
     * Creates a GraphicsContext that hosts view-backed graphics layers inside the given ViewGroup.
     *
     * @param layerContainer The ViewGroup used to contain and manage view-based graphics layers.
     * @return A GraphicsContext implementation bound to the provided ViewGroup.
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
                 * Ignores configuration changes; no action is taken when the device configuration changes.
                 *
                 * @param newConfig The new configuration (ignored).
                 */
                override fun onConfigurationChanged(newConfig: Configuration) {
                    // NO-OP
                }

                /**
                 * Intentionally ignores low-memory notifications.
                 *
                 * This implementation performs no action when the system reports low memory.
                 */
                @Suppress("OVERRIDE_DEPRECATION") // b/407491706
                override fun onLowMemory() {
                    // NO-OP
                }

                /**
                 * Handles system memory pressure notifications by clearing the cached shadow context when
                 * the trim level indicates background-or-worse memory pressure.
                 *
                 * @param level The memory trim level delivered by the system (see `ComponentCallbacks2`). If
                 * `level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND`, the cached shadow context is cleared.
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
                 * Registers the component callbacks when the view is attached to a window.
                 *
                 * @param v The view that was attached to the window whose context is used to register callbacks.
                 */
                override fun onViewAttachedToWindow(v: View) {
                    // If the View is attached to the window again, re-add the component
                    // callbacks
                    registerComponentCallback(v.context)
                }

                /**
                 * Handle the view being detached from the window by unregistering component callbacks and clearing the shadow cache.
                 *
                 * @param v The view that was detached from its window.
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
     * Clears the cached ShadowContext and releases its resources.
     *
     * If a shadow context is present, invokes its cache-clear routine and removes the cached reference.
     */
    private fun clearShadowCache() {
        shadowCache?.clearCache()
        shadowCache = null
    }

    /**
     * Registers the internal ComponentCallbacks2 with the application's context if it is not already registered.
     *
     * @param context The context whose applicationContext will be used to register the callback.
     */
    private fun registerComponentCallback(context: Context) {
        if (!componentCallbackRegistered) {
            context.applicationContext.registerComponentCallbacks(componentCallback)
            componentCallbackRegistered = true
        }
    }

    /**
     * Unregisters the ComponentCallbacks2 from the application's context if currently registered.
     *
     * If registered, the callback is removed from the application context and the internal
     * registration flag is cleared.
     */
    private fun unregisterComponentCallback(context: Context) {
        if (componentCallbackRegistered) {
            context.applicationContext.unregisterComponentCallbacks(componentCallback)
            componentCallbackRegistered = false
        }
    }

    /**
     * Creates a new GraphicsLayer associated with this context's owner ViewGroup, choosing a
     * platform-appropriate implementation based on runtime compatibility and API level.
     *
     * @return A ready-to-use GraphicsLayer that wraps a platform-specific implementation suitable
     *         for the current device and configuration.
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
     * Releases resources associated with the specified graphics layer.
     *
     * The operation is performed in a thread-safe manner.
     *
     * @param layer The graphics layer to release and free any held resources for.
     */
    override fun releaseGraphicsLayer(layer: GraphicsLayer) {
        synchronized(lock) { layer.release() }
    }

    /**
     * Obtain the DrawChildContainer hosted by the given ViewGroup, creating and attaching one if absent.
     *
     * @param ownerView ViewGroup that will host the container.
     * @return The existing or newly created DrawChildContainer attached to `ownerView`.
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
         * Obtain the platform unique drawing id for the given view when available.
         *
         * @param view The view whose unique drawing id is requested.
         * @return `view.uniqueDrawingId` on Android Q (API 29) and above, `-1` on earlier API levels.
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
 * Retrieve the unique drawing id for a view.
 *
 * @param view The view to retrieve the unique drawing id from.
 * @return The view's unique drawing id (available on API 29+).
 */
@JvmStatic fun getUniqueDrawingId(view: View) = view.uniqueDrawingId
    }
}