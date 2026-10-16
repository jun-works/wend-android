# Wend 릴리스 R8 규칙.
# Hilt · Room · WorkManager · kotlinx.serialization · Navigation · Coil · Firebase 는
# 라이브러리 안에 R8 규칙이 들어 있어서 따로 적지 않는다.

# Crashlytics 스택에서 줄 번호를 읽을 수 있게 남기고, 원래 파일 이름은 숨긴다.
# 이름 되돌리기용 mapping.txt 는 Crashlytics 플러그인이 릴리스 빌드 때 올린다.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Crashlytics 가 사용자 정의 예외 이름을 그대로 보여 주도록
-keep public class * extends java.lang.Exception
