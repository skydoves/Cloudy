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
package docs.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import docs.theme.DocsTheme

@Composable
fun CodeBlock(code: String, modifier: Modifier = Modifier, language: String = "kotlin") {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(DocsTheme.colors.codeBackground)
      .padding(16.dp),
  ) {
    SelectionContainer {
      Column {
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
          Text(
            text = highlightKotlinSyntax(code.trimIndent()),
            style = DocsTheme.typography.code,
          )
        }
      }
    }
  }
}

@Composable
private fun highlightKotlinSyntax(code: String): AnnotatedString {
  val colors = DocsTheme.colors

  val keywords = setOf(
    "fun", "val", "var", "class", "object", "interface", "sealed",
    "data", "enum", "when", "if", "else", "for", "while", "return",
    "import", "package", "private", "public", "internal", "protected",
    "override", "open", "abstract", "suspend", "inline", "expect", "actual",
    "by", "companion", "init", "get", "set", "is", "in", "as", "true", "false",
    "null", "this", "super", "it", "constructor", "lateinit", "lazy",
  )

  val annotations = setOf("@Composable", "@Stable", "@Immutable", "@OptIn")

  return buildAnnotatedString {
    var i = 0
    while (i < code.length) {
      when {
        // String literals
        code[i] == '"' -> {
          val end = findStringEnd(code, i)
          withStyle(SpanStyle(color = colors.codeString)) {
            append(code.substring(i, end))
          }
          i = end
        }
        // Comments
        code.substring(i).startsWith("//") -> {
          val end = code.indexOf('\n', i).let { if (it == -1) code.length else it }
          withStyle(SpanStyle(color = colors.codeComment)) {
            append(code.substring(i, end))
          }
          i = end
        }
        // Numbers
        code[i].isDigit() -> {
          val end = findNumberEnd(code, i)
          withStyle(SpanStyle(color = colors.codeNumber)) {
            append(code.substring(i, end))
          }
          i = end
        }
        // Annotations
        code[i] == '@' -> {
          val end = findWordEnd(code, i + 1) // Skip '@' and find word end
          val word = code.substring(i, end)
          withStyle(SpanStyle(color = colors.codeKeyword)) {
            append(word)
          }
          i = end
        }
        // Words (keywords or identifiers)
        code[i].isLetter() || code[i] == '_' -> {
          val end = findWordEnd(code, i)
          val word = code.substring(i, end)
          if (keywords.contains(word)) {
            withStyle(SpanStyle(color = colors.codeKeyword)) {
              append(word)
            }
          } else {
            withStyle(SpanStyle(color = colors.codeForeground)) {
              append(word)
            }
          }
          i = end
        }
        // Other characters
        else -> {
          withStyle(SpanStyle(color = colors.codeForeground)) {
            append(code[i])
          }
          i++
        }
      }
    }
  }
}

private fun findStringEnd(code: String, start: Int): Int {
  var i = start + 1
  while (i < code.length) {
    if (code[i] == '"') {
      // Count preceding backslashes
      var backslashCount = 0
      var j = i - 1
      while (j >= start && code[j] == '\\') {
        backslashCount++
        j--
      }
      // Quote is escaped only if preceded by odd number of backslashes
      if (backslashCount % 2 == 0) {
        return i + 1
      }
    }
    i++
  }
  return code.length
}

private fun findNumberEnd(code: String, start: Int): Int {
  var i = start
  while (i < code.length &&
    (code[i].isDigit() || code[i] == '.' || code[i] == 'f' || code[i] == 'L')
  ) {
    i++
  }
  return i
}

private fun findWordEnd(code: String, start: Int): Int {
  var i = start
  while (i < code.length && (code[i].isLetterOrDigit() || code[i] == '_')) {
    i++
  }
  return i
}
