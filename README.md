# Günlük Planım

Verilen kayıt/giriş ve liste yönetimi özelliklerini bir alışkanlık defteri fikrinde birleştirdim. Amaç, günlük rutinleri ve tek seferlik işleri takvim üzerinden takip edebilmek. Uygulamayı Kotlin ve Jetpack Compose ile geliştirdim; internet bağlantısı gerekmiyor.

## Neler yapılabiliyor?

Kayıt ekranı ad, e-posta ve şifreyi kontrol ediyor. Giriş yaptıktan sonra oturum uygulamayı kapatıp açınca da korunuyor. Takvimden bir gün seçerek o güne ait planları görebiliyorsunuz. Yeni aktivite eklerken **her gün** tekrarlanmasını veya yalnızca seçtiğiniz tarihte görünmesini seçebilirsiniz. Bir alışkanlığı tamamlamak sadece o günü işaretler; ertesi gün yeniden yapılacaklar arasında görünür.

Aktiviteler düzenlenebilir ve silinebilir. Liste içinde arama yapabilir, tamamlanma durumuna ve kategoriye göre filtreleyebilir, oluşturulma zamanına veya başlığa göre sıralayabilirsiniz. Üstteki kart, seçili günün ne kadarını tamamladığınızı gösterir.

## Çalıştırma

1. Depoyu klonlayın: `git clone https://github.com/Canzgn/TaskManager.git`
2. Android Studio'da `TaskManager` klasörünü açın ve Gradle senkronizasyonunun bitmesini bekleyin.
3. Bir Android telefon veya emülatör seçip **Run 'app'** düğmesine basın.
4. Açılış ekranından yeni bir hesap oluşturun. Hazır bir kullanıcı hesabı veya sunucu kurulumu gerekmiyor.

İlk açılışta Android Studio gerekli SDK/Gradle bileşenlerini indirmek isteyebilir. Proje JDK 21 ve Android SDK 36.1 ile derlendi; minimum cihaz sürümü Android 7.0 (API 24). Gradle Wrapper depoda mevcut, Kotlin'i ayrıca kurmanız gerekmiyor.

## Kısa teknik not

Aktiviteler ve günlük tamamlanma kayıtları SQLite'ta tutuluyor. Oturum için SharedPreferences kullanılıyor. Şifreler açık metin olarak değil, salt eklenerek PBKDF2 ile özetlenmiş hâlde saklanıyor. Veriler cihazlar arasında eşitlenmiyor.
