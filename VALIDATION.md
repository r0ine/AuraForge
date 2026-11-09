# AuraForge 1.2.0 doğrulaması

8 Kasım 2026, Windows üzerinde Temurin Java 21 ve Paper 1.21.4 build 232.

`gradlew.bat build integrationJar` geçti. Teslim dosyası `build/libs/AuraForge-1.2.0.jar`;
kontrol eklentisi ve test sınıfları bu jar'a dahil değil. Üretim özellikleri Kotlin ile yazıldı.

| Kontrol | Sonuç |
|---|---|
| Kotlin derlemesi ve paketleme | Geçti |
| Birim testleri | 8 test, 0 hata |
| Paper üzerindeki menü, savaş ve ekonomi kontrolleri | 257 doğrulama geçti |
| Kapatıp açtıktan sonra kayıtlar | 6 doğrulama geçti |
| Doğallık taraması | 0 kritik, 0 uyarı; eski README'deki oyun terimi için 1 bilgi |
| AI hakem | Atlandı; iki servis de HTTP 410 döndürdü |
| `git diff --check` | Geçti |

Sunucu kontrolleri 20 mührün işlenmesini, maliyetini, kayıtlarını, çıkarılmasını ve etkilerini;
10 büyünün hasar/destek etkilerini, mana tüketimini ve beklemelerini; beş setin parçalarını,
sözleşme/günlük ödüllerinin tekrar alınamamasını, sökümün eşya değişimine karşı kontrolünü kapsar.
Mühür yuvaları 10. ve 20. seviyede sınandı. Menüde shift, sayı tuşu, çift tık ve eşya bırakma
işlemlerinin iptal edildiği kontrol edildi. Duvar arkasındaki hedef, kapalı büyü yetkisi,
iptal edilmiş hasar ve spawner ödülü senaryoları da geçti.

Mineflayer istemcisiyle yapılan gerçek yakın dövüş saldırısı hasar verdi, Kor İzi'ni tetikledi
ve kombo başlattı. Yeniden başlatmada XP/öz, öğrenilmiş ve seçili büyüler, sözleşme, günlük,
bekleme süresi ve eşya mührü korundu.

Testler 127.0.0.1:25586 üzerinde ayrı sunucuda çalıştı. 22222 portundaki mevcut sunucu yeniden
başlatılmadı ve jar'ı değiştirilmedi. Canlı Folia sunucusunda çalışma testi yapılmadı.

Kanıt dosyaları:

- `build/reports/tests/test/index.html`
- `build/verification/phase-1.log`: `ADVENTURE_CHECKS_PASSED 257`
- `build/verification/phase-2.log`: `ADVENTURE_PERSISTENCE_PASSED 6`

Teslim jar'ının SHA-256 özeti:

```text
6713E21CC7AB345237ABC115835CAA1E28ED552A0E16746B8B6E85940A0B32C5
```
