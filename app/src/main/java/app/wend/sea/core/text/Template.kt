package app.wend.sea.core.text

import androidx.compose.ui.text.AnnotatedString

private val Placeholder = Regex("%(\\d+)\\\$s")

/**
 * 문자열 리소스를 글자 그대로 넣되 `%1$s`, `%2$s` … 자리는 [parts] 로 채운다(굵게·색·아이콘 같은 꾸밈용).
 * 언어마다 어순이 달라도 꾸밈이 제자리에 붙는다.
 */
fun AnnotatedString.Builder.appendTemplate(template: String, vararg parts: AnnotatedString.Builder.() -> Unit) {
    var last = 0
    for (m in Placeholder.findAll(template)) {
        append(template.substring(last, m.range.first))
        parts[m.groupValues[1].toInt() - 1]()
        last = m.range.last + 1
    }
    append(template.substring(last))
}
