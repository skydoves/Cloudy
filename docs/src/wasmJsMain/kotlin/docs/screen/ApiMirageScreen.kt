/*
 * Designed and developed by 2022 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package docs.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import docs.component.Callout
import docs.component.CalloutType
import docs.component.CodeBlock
import docs.theme.DocsTheme

@Composable
fun ApiMirageScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(32.dp),
  ) {
    Text(
      text = "Mirage",
      style = DocsTheme.typography.h1,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Mirage applies an open shader-effect plan to any content through a single " +
        "modifier. One Modifier.mirage { } block declares an ordered list of optics — an " +
        "optic pairs an AGSL (Android) / SKSL (Skia) kernel with the uniforms it reads. The " +
        "library ships thin-film, specular, and foil presets, and consumers can author their " +
        "own optics through the same public API with no library change.",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Experimental opt-in
    Callout(
      text = "Mirage is experimental. Opt in with @OptIn(ExperimentalMirage::class) or propagate " +
        "the annotation. It is excluded from the stable ABI and may change between releases.",
      type = CalloutType.NOTE,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Signatures
    Text(
      text = "Signatures",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        // Applies an ordered optic plan to the content it modifies. Node-based (not @Composable):
        // the plan block runs once to fix the stages; each stage's params block re-runs per draw.
        @ExperimentalMirage
        fun Modifier.mirage(
          clock: MirageClock = MirageClock.Auto,
          enabled: Boolean = true,
          plan: MirageScope.() -> Unit,
        ): Modifier

        // Declared inside the plan block:
        interface MirageScope {
          // Content-transforming stage; chains in declared order (content -> f1 -> f2 -> screen).
          fun <P : MirageParams> filter(optic: FilterOptic<P>, params: (P.() -> Unit)? = null)

          // Overlay drawn over the filtered result; composites in declared order.
          fun <P : MirageParams> overlay(
            optic: GenerateOptic<P>,
            blendMode: BlendMode = BlendMode.SrcOver,
            params: (P.() -> Unit)? = null,
          )
        }
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Quick start
    Text(
      text = "Quick start",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = "Declare one filter stage with a preset. Lens-shaped presets (Specular, the " +
        "thin-film family, Foil) read a shared lens framing: lensCenter defaults to the " +
        "content origin (Offset.Zero), so seed it with the pane center for a centered lens. " +
        "The params block runs per draw, so reading snapshot state inside it invalidates only " +
        "the draw — never a recomposition.",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        @OptIn(ExperimentalMirage::class)
        @Composable
        fun SpecularPoster(image: Painter) {
          var center by remember { mutableStateOf(Offset.Zero) }

          Box(
            modifier = Modifier
              // Seed the lens center from the pane size (defaults to the content origin otherwise).
              .onSizeChanged { center = Offset(it.width / 2f, it.height / 2f) }
              .mirage {
                filter(MirageOptics.Specular) {
                  lensCenter(center)
                  lensSize(Size(350f, 350f))
                  cornerRadius(50f)
                }
              },
          ) {
            Image(painter = image, contentDescription = null)
          }
        }
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Preset gallery
    Text(
      text = "Preset gallery",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    PresetTable()

    Spacer(modifier = Modifier.height(12.dp))

    Callout(
      text = "The five thin-film looks (Chromatic, OilSlick, SoapBubble, MetallicFoil, Pearl) " +
        "are one GPU program at different uniform defaults, so switching between them never " +
        "recompiles. Applying a lens-shaped preset with no params block reproduces the " +
        "built-in liquid-glass framing.",
      type = CalloutType.INFO,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Clock
    Text(
      text = "Clock",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = "The clock drives the standard mirageTime uniform (seconds since attach). The " +
        "plan owns a single frame loop, so every time-driven stage advances off one clock. " +
        "Auto only spins the loop when some stage's kernel actually references mirageTime.",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        // Default: drives mirageTime; the loop runs only if a stage references it.
        Modifier.mirage(clock = MirageClock.Auto) { /* ... */ }

        // Freezes time at its last value; no frame loop runs.
        Modifier.mirage(clock = MirageClock.Paused) { /* ... */ }

        // A constant mirageTime with no loop — deterministic, for screenshot tests of animated optics.
        Modifier.mirage(clock = MirageClock.Fixed(seconds = 1.5f)) { /* ... */ }
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Overlays
    Text(
      text = "Overlays",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = "A filter transforms the content; an overlay (a content-free generator such as " +
        "Foil) is drawn over the filtered result. Filters chain in declared order, then " +
        "overlays composite over them in declared order under blendMode. The plan orders them " +
        "correctly regardless of which you declare first.",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        @OptIn(ExperimentalMirage::class)
        @Composable
        fun ChainedPoster(center: Offset) {
          val framing: MirageLensParams.() -> Unit = {
            lensCenter(center)
            lensSize(Size(350f, 350f))
            cornerRadius(50f)
          }

          Box(
            modifier = Modifier.mirage {
              // Refract the content, then composite the foil sparkle over it (Screen blend).
              filter(MirageOptics.OilSlick) { framing() }
              overlay(MirageOptics.Foil, blendMode = BlendMode.Screen) { framing() }
            },
          ) { /* ... */ }
        }
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Author your own optic
    Text(
      text = "Author your own optic",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = "An optic is a kernel plus a MirageParams subclass. Declare uniforms with by " +
        "uniform(...) / by uniformColor(...) — the property name is the shader uniform " +
        "identifier, and declaration order is the binding order. Optic.composite authors a " +
        "full half4 main(float2 xy) that samples the content through the auto-declared content " +
        "shader (content.eval(xy)); Optic.colorize authors only half4 kernel(float2 p, half4 " +
        "src) and codegen wraps the content sampling for you.",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        @OptIn(ExperimentalMirage::class)
        class TintVignetteParams : MirageParams() {
          // Property name == shader uniform identifier; declaration order == binding order.
          val tint: UColor by uniformColor(Color(0xFF5C6BC0))
          val amount: UFloat by uniform(0.4f)
          val vignette: UFloat by uniform(0.6f)
        }

        // A composite optic authors the whole main(); it may sample the content freely via content.eval.
        // The standard uniform mirageResolution is auto-declared when referenced by name.
        @OptIn(ExperimentalMirage::class)
        val TintVignette = Optic.composite(
          name = "tint-vignette",
          paramsFactory = ::TintVignetteParams,
          agsl = TINT_VIGNETTE_KERNEL,
          sksl = TINT_VIGNETTE_KERNEL,
        )
      """,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        // Same source compiles as AGSL (Android) and SKSL (Skia). No derivatives, no preprocessor,
        // no raw frag-coord — the runtime hands coordinates in via main's `xy` argument.
        private const val TINT_VIGNETTE_KERNEL = ""${'"'}
          half4 main(float2 xy) {
            half4 src = content.eval(xy);
            // Mix a flat tint into the content.
            half3 tinted = mix(src.rgb, tint.rgb, half(amount));
            // Radial darkening toward the edges of the pane.
            float2 uv = xy / mirageResolution;
            float d = distance(uv, float2(0.5));
            float v = 1.0 - vignette * smoothstep(0.3, 0.75, d);
            return half4(tinted * half(v), src.a);
          }
        ""${'"'}
      """,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        // Apply the authored optic exactly like a preset.
        Modifier.mirage {
          filter(TintVignette) {
            tint(Color(0xFF7E57C2))
            amount(0.5f)
          }
        }
      """,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Callout(
      text = "Lint constraints: a kernel must not use derivative functions (fwidth / dFdx / " +
        "dFdy), any preprocessor directive (#), or the raw sk_FragCoord builtin — none compile " +
        "as a runtime shader. A Colorize kernel reaches content only through its src argument; " +
        "a Generate overlay must not reference content at all.",
      type = CalloutType.WARNING,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Notes
    Text(
      text = "Notes",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = """
        • The shader path needs Android API 33+; Skia targets (iOS, macOS, Desktop, Web) run the full effect.
        • The plan block runs once (its stage list is the node's equality key); each stage's params block runs per draw.
        • Compiled programs are cached process-wide by shader source, so reusing an optic or toggling enabled never recompiles.
        • Preset defaults live in the params, not as shader constants, so every value is animatable and changing one never recompiles.
        • enabled = false bypasses the whole plan and passes the content through unmodified.
      """.trimIndent(),
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(48.dp))
  }
}

@Composable
private fun PresetTable() {
  val shape = RoundedCornerShape(8.dp)

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .border(1.dp, DocsTheme.colors.divider, shape),
  ) {
    // Header
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(DocsTheme.colors.surfaceVariant)
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = "Preset",
        style = DocsTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = DocsTheme.colors.onSurface,
        modifier = Modifier.weight(1f),
      )
      Text(
        text = "Family",
        style = DocsTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = DocsTheme.colors.onSurface,
        modifier = Modifier.weight(1f),
      )
      Text(
        text = "Description",
        style = DocsTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = DocsTheme.colors.onSurface,
        modifier = Modifier.weight(2f),
      )
    }

    PresetRow(
      "Specular",
      "Specular",
      "The liquid-glass specular glint: a moving focal hotspot plus a Blinn rim. Lens-shaped.",
    )
    PresetRow(
      "Chromatic",
      "Thin-film",
      "The default thin-film iridescence (Newton's rings). Lens-shaped.",
    )
    PresetRow(
      "OilSlick",
      "Thin-film",
      "High band count, wide RGB spread, near-zero floor — a saturated, dark-based rainbow.",
    )
    PresetRow(
      "SoapBubble",
      "Thin-film",
      "Few wide bands with a high floor and strong wash-out — pale, pastel iridescence.",
    )
    PresetRow(
      "MetallicFoil",
      "Thin-film",
      "Dark floor plus a Fresnel rim boost toward white at the edge — a sharp metallic sheen.",
    )
    PresetRow(
      "Pearl",
      "Thin-film",
      "High floor, strong wash-out, and a rim boost — a soft, luminous, low-saturation lustre.",
    )
    PresetRow(
      "Foil",
      "Overlay",
      "A content-free generator (glare + flowing rainbow + sparkle). Declare via overlay(...).",
    )
    PresetRow(
      "Duotone",
      "Colorize",
      "A point-wise duotone grade mapping luminance onto a shadow -> highlight gradient. " +
        "No lens framing.",
    )
  }
}

@Composable
private fun PresetRow(name: String, family: String, description: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(DocsTheme.colors.surface)
      .padding(12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = "MirageOptics.$name",
      style = DocsTheme.typography.code,
      color = DocsTheme.colors.primary,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = family,
      style = DocsTheme.typography.bodySmall,
      color = DocsTheme.colors.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = description,
      style = DocsTheme.typography.bodySmall,
      color = DocsTheme.colors.onSurfaceVariant,
      modifier = Modifier.weight(2f),
    )
  }
}
