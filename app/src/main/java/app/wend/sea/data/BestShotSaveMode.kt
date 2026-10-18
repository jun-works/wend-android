package app.wend.sea.data

/** 베스트샷 자체는 분석 세션 값이지만, 저장 방식은 사용자가 바꾸기 전까지 유지한다. */
enum class BestShotSaveMode {
    UNSET,
    APP_ONLY,
    GALLERY;

    companion object {
        fun fromStored(value: String?): BestShotSaveMode =
            entries.firstOrNull { it.name == value } ?: UNSET
    }
}
