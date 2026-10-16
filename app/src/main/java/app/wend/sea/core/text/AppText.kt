package app.wend.sea.core.text

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import app.wend.sea.R
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 화면 밖(ViewModel·데이터·알림)에서 쓰는 문자열 리소스. 앱 언어가 바뀌면 Application 리소스도 같이 바뀐다.
 * Compose 안에서는 stringResource 를 쓴다.
 */
object AppText {
    private lateinit var context: Context

    fun init(app: Context) {
        context = app.applicationContext
    }

    fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
        context.resources.getQuantityString(id, count, *args)

    /** 날짜 형식은 화면 문구와 같은 언어로 — 리소스가 영어로 떨어지면 영어 날짜 */
    val locale: Locale get() = Locale.forLanguageTag(context.getString(R.string.format_locale))

    /** [id] 는 DateTimeFormatter 패턴 문자열 리소스 */
    fun dateFormat(@StringRes id: Int): DateTimeFormatter = DateTimeFormatter.ofPattern(context.getString(id), locale)
}
