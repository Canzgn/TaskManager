# Günlük Planım (TaskManager)

Kotlin ve Jetpack Compose ile geliştirilmiş çevrimdışı günlük planlayıcı ve alışkanlık takip uygulaması.

## Özellikler

- Ad, e-posta ve şifre doğrulamalı kayıt
- Giriş, kalıcı oturum ve çıkış
- Kullanıcıya özel aktivite ekleme, listeleme, detay, düzenleme ve silme (CRUD)
- Aylık takvim görünümü, gün seçimi ve takvimde plan/tamamlanma işaretleri
- Her gün tekrarlanan alışkanlık veya tarihli tek seferlik plan oluşturma
- Alışkanlıkları her gün bağımsız olarak tamamlandı/yapılacak işaretleme
- Başlık ve açıklamada arama
- Durum ve kategoriye göre filtreleme
- Tarihe veya başlığa göre sıralama
- Seçilen günün tamamlanan aktivitelerini gösteren ilerleme özeti

## Çalıştırma

1. Depoyu klonlayın: `git clone https://github.com/Canzgn/TaskManager.git`
2. Android Studio'da **Open** ile klonlanan `TaskManager` klasörünü açın.
3. İlk açılışta Gradle/Android SDK bileşenlerinin indirilmesine izin verin ve senkronizasyonun tamamlanmasını bekleyin. İnternet bağlantısı gerekir.
4. Android 7.0 (API 24) veya üzeri bir telefon ya da Android emülatörü seçip **Run 'app'** düğmesine basın.
5. Uygulamada **Kayıt ol** ile kendi hesabınızı oluşturun; örnek hesap veya sunucu kurulumu gerekmez.

Gereksinimler: Android Studio (AGP 9.1.1 ve Gradle 9.3.1 ile uyumlu güncel sürüm), Android SDK 36.1, JDK 21 ve Android 7.0+ cihaz/emülatör. Gradle Wrapper depoda bulunur; Kotlin veya Gradle'ı ayrıca kurmanız gerekmez. `local.properties` makineye özeldir ve depoya eklenmez; Android Studio SDK yolunu kendisi oluşturur.

## Hızlı deneme

1. Kayıt olup yeni bir **Her gün** alışkanlığı ekleyin.
2. Bugün tamamlandı olarak işaretleyin; takvimde başka bir güne geçince o günün işaretinin bağımsız olduğunu görün.
3. Takvimden ileri bir gün seçip **Tek seferlik** aktivite ekleyin.
4. Başlık/açıklamada arama, durum/kategori filtreleri, sıralama, detay, düzenleme ve silmeyi deneyin.

## Teknik not

Kullanıcılar, aktiviteler ve güne özel tamamlanma kayıtları cihazdaki SQLite veritabanında tutulur. Önceki veritabanı sürümündeki görevler ve tamamlanma durumları silinmeden yeni sürüme taşınır. Şifreler rastgele salt ve PBKDF2 ile özetlenir; açık metin saklanmaz. Oturum kimliği SharedPreferences içinde tutulur. Uygulama bir demo olduğu için sunucu hesabı veya cihazlar arası senkronizasyon içermez.
