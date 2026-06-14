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
package com.skydoves.cloudy

/**
 * Marks the open shader-recipe API ([Modifier.shaderEffect], [ShaderRecipe], [ShaderEffectScope],
 * [ShaderInputMode]) as experimental.
 *
 * This API lets callers inject arbitrary AGSL / SKSL shader bodies and bind their uniforms, so its
 * surface (the recipe author contract, the scope shape, the modifier parameters) may still change.
 * Opt in with `@OptIn(ExperimentalShaderEffect::class)` or by propagating the annotation.
 */
@RequiresOptIn(
  level = RequiresOptIn.Level.WARNING,
  message = "The open shader-recipe API is experimental and may change.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
// Self-annotated so binary-compatibility validation treats the marker itself as a non-public
// (opt-in) declaration and keeps it out of the committed .api dumps, matching the experimental
// members it guards. Declaring an opt-in marker requires no opt-in, so this is not circular.
@ExperimentalShaderEffect
public annotation class ExperimentalShaderEffect
