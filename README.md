# TaskManager

Kotlin ve Jetpack Compose ile geliştirilmiş çevrimdışı görev yönetimi uygulaması.

## Özellikler

- Ad, e-posta ve şifre doğrulamalı kayıt
- Giriş, kalıcı oturum ve çıkış
- Kullanıcıya özel görev ekleme, listeleme, detay, düzenleme ve silme
- Görevi tamamlandı/yapılacak olarak işaretleme
- Başlık ve açıklamada arama
- Duruma göre filtreleme
- Tarihe veya başlığa göre sıralama

## Çalıştırma

1. Projeyi Android Studio ile açın.
2. Gradle senkronizasyonunun bitmesini bekleyin.
3. Android cihaz veya emulator seçip **Run app** düğmesine basın.

Minimum Android sürümü: API 24 (Android 7.0).

## Teknik not

Kullanıcılar ve görevler cihazdaki SQLite veritabanında tutulur. Şifreler rastgele salt ve PBKDF2 ile özetlenir; açık metin saklanmaz. Oturum kimliği SharedPreferences içinde tutulur. Uygulama bir demo olduğu için sunucu hesabı veya cihazlar arası senkronizasyon içermez.
