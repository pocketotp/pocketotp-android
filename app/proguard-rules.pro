# R8 is opt-in (-Ppocketotp.minify=true). Libraries (OkHttp, WorkManager, ZXing, Compose) ship their own consumer rules.
# The app uses no reflection; keep line numbers readable in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
