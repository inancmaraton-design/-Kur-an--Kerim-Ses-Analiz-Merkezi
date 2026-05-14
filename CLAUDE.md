\## Workflow Orchestration





\### 1. Plan Mode Default



\- Enter plan mode for ANY non-trivial task (3+ steps or architectural decisions)



\- If something goes sideways, STOP and re-plan immediately



\- Use plan mode for verification steps, not just building



\- Write detailed specs upfront to reduce ambiguity





\### 2. Subagent Strategy



\- Use subagents liberally to keep main context window clean



\- Offload research, exploration, and parallel analysis to subagents



\- For complex problems, throw more compute at it via subagents



\- One task per subagent for focused execution





\### 3. Self-Improvement Loop



\- After ANY correction from the user: update tasks/lessons.md with the pattern



\- Write rules for yourself that prevent the same mistake



\- Ruthlessly iterate on these lessons until mistake rate drops



\- Review lessons at session start for relevant project





\### 4. Verification Before Done



\- Never mark a task complete without proving it works



\- Diff behavior between main and your changes when relevant



\- Ask yourself: "Would a staff engineer approve this?"



\- Run tests, check logs, demonstrate correctness





\### 5. Demand Elegance (Balanced)



\- For non-trivial changes: pause and ask "is there a more elegant way?"



\- If a fix feels hacky: "Knowing everything I know now, implement the elegant solution"



\- Skip this for simple, obvious fixes -- don't over-engineer



\- Challenge your own work before presenting it





\### 6. Autonomous Bug Fixing



\- When given a bug report: just fix it. Don't ask for hand-holding



\- Point at logs, errors, failing tests -- then resolve them



\- Zero context switching required from the user



\- Go fix failing CI tests without being told how





\## Task Management





1\. Plan First: Write plan to tasks/todo.md with checkable items



2\. Verify Plan: Check in before starting implementation



3\. Track Progress: Mark items complete as you go



4\. Explain Changes: High-level summary at each step



5\. Document Results: Add review section to tasks/todo.md



6\. Capture Lessons: Update tasks/lessons.md after corrections





\## Core Principles





\- Simplicity First: Make every change as simple as possible. Impact minimal code.



\- No Laziness: Find root causes. No temporary fixes. Senior developer standards.



\- Minimal Impact: Only touch what's necessary. No side effects with new bugs.





\## Gradle \& Build

\- \*\*ZORUNLU:\*\* Gradle 8.9 kullan (8.9 ile çalışır, 9.x hata yapıyor)

\- compileSdk = 34

\- targetSdk = 34

\- AGP 9.x ile uyumsuzluk yaşanıyor → downgrade et

\- Transform cache corruption → gradle clean \& rebuild



\## Kod Yazım Kuralları

\- \*\*Kotlin naming:\*\* camelCase (değişkenler), PascalCase (sınıflar)

\- \*\*Null safety:\*\* non-null'u baştan kontrol et

\- \*\*Coroutines:\*\* suspend function'lar için withContext kullan

\- \*\*Thread:\*\* UI işlemleri Main thread'de, ağ/IO işlemleri IO dispatcher'da



\## Yaygın Sorunlar \& Çözümler

1\. \*\*UTF-8 BOM sorunu (PowerShell):\*\*

&#x20;  - Dosya yazarken: \[System.IO.File]::WriteAllText(\[path], \[content], \[UTF8Encoding(false)])

&#x20;  - Başında BOM olmuyor



2\. \*\*OkHttp Connection Leak:\*\*

&#x20;  - Connection pool'u kontrol et

&#x20;  - Timeout ayarla (connectTimeout, readTimeout)



3\. \*\*VPN Service:\*\*

&#x20;  - Foreground service permission kontrol

&#x20;  - NotificationCompat kullan

&#x20;  - onStartCommand() düzgün implement et



4\. \*\*JSON Parse:\*\*

&#x20;  - Hardcoded key'ler yerine safe parsing kullan

&#x20;  - Exception handling ekle



5\. \*\*Prompt Injection Risk:\*\*

&#x20;  - User input'ını sanitize et

&#x20;  - Unsanitized string'leri API'ye gönderme



\## AI Integration

\- Gemini/Groq/OpenRouter API'ler mevcut

\- API key'leri güvenli tut

\- Rate limiting ekle



\## Kotlin Best Practices

\- Data class kullan (equals/hashCode otomatik)

\- Extension function'lar açık yaz

\- Sealed class'lar pattern matching için

\- Scope function'lar (let, apply, also) uygun yerde



\## Git \& Version Control

\- Commit message'ları Türkçe yaz

\- Gradle cache'i .gitignore'a ekle

\- Local.properties'i commit'leme



\## Debug \& Logging

\- Timber veya Log kullan

\- Sensitive veriyi LOGLAMA (API key, token, password vb.)

\- Debug build'de verbose logging, release'de minimal



\## Sorun Çözmede Adımlar

1\. Gradle clean \& rebuild yap

2\. AGP/compileSdk versiyonlarını kontrol et

3\. Stack trace'i tam göster

4\. Kotlin syntax hataları kontrol et

5\. Module bağlantılarını (settings.gradle) kontrol et



---

## Proje Analiz Referansı

Detaylı proje analizi, mimari incelemesi, tespit edilen hatalar ve geliştirme önerileri için bkz: `PROJE_ANALIZ_RAPORU.md`

