# AuraForge

Paper ve Folia için Kotlin ile yazılmış bir dövme tezgahı eklentisi. Oyuncu dünyaya bir tezgah kurar,
malzemeleri üstüne koyar, malzemeler tezgahın üstünde dönmeye başlar. Dövme sırasında ekranda kayan bir
imleç çıkar; doğru anda vuran oyuncu daha kaliteli bir eşya alır. Çıkan silah ve zırhların sağ tık ya da
eğilip zıplamayla tetiklenen yetenekleri vardır.

- Paper 1.21.4 ve üzeri, Folia (`folia-supported: true`)
- Java 21, Kotlin 2.4, kotlinx.coroutines, kotlinx.serialization

## Oyun içinde

1. **Forge Çekirdeği** al: `/af give` ya da çalışma masasında
   (demir külçe, blaze tozu, örs; ortada örs, köşelerde demir, kenarlarda blaze tozu).
2. Çekirdekle bir bloğa sağ tıkla, üstüne tezgah kurulur.
3. Elinde malzemeyle tezgaha sağ tıkla, her tıkta bir tane konur. Sol tık son koyduğunu geri verir.
4. Malzemeler bir tarifle birebir eşleşince hologram **Hazır** yazar.
5. Eğilerek boş elle sağ tıkla (ya da menüden *Dövmeye Başla*). Her turda imleç barın üstünde kayar;
   yeşil bölgeye geldiğinde eğil ya da tezgaha sağ tıkla. Altın bölge kusursuz vuruştur.
6. Tur puanlarının ortalaması kaliteyi verir (1-100): Çatlak, Sağlam, Usta İşi, Efsanevi.

Boş elle normal sağ tık tezgah menüsünü açar: malzemeleri tek tek ya da toplu geri alma, tarif kitabı,
dövmeyi başlatma ve tezgahı toplama. Envanterden shift+tık ile de malzeme konabilir.

### Yetenekler

| Yetenek | Etki |
|---|---|
| Atılma (`dash`) | Baktığın yöne yatay savrulma, kısa süre düşme hasarı yok |
| Sıçrayış (`leap`) | Yukarı ve ileri fırlama, iniş hasarı yok |
| Şok Dalgası (`shockwave`) | Çevredeki canlılara hasar ve geri itme |
| Muhafız (`guard`) | Direnç ve emilim etkisi |

Silah ve kalkanlar sağ tıkla, zırhlar giyiliyken **eğilip zıplayınca** çalışır. Kalite gücü artırır ve
bekleme süresini kısaltır. Aynı yeteneği taşıyan zırh parçaları (varsayılan 2 ve üzeri) set bonusu verir.

## Komutlar ve yetkiler

| Komut | Yetki | Açıklama |
|---|---|---|
| `/af recipes` | `auraforge.use` | Tarifleri listeler |
| `/af list` | `auraforge.use` | Kendi tezgahlarının konumu |
| `/af stats [oyuncu]` | `auraforge.use` | Dövülen eşya, kusursuz vuruş, en iyi kalite |
| `/af give [oyuncu] [adet]` | `auraforge.give` | Forge Çekirdeği verir |
| `/af item <tarif> [kalite] [oyuncu]` | `auraforge.give` | Hazır yetenekli eşya verir (test için) |
| `/af reload` | `auraforge.reload` | Ayarları ve tarifleri yeniden yükler |

`auraforge.place` tezgah kurmayı, `auraforge.admin` başkasının tezgahını kullanmayı ve oyuncu başına
tezgah sınırını aşmayı sağlar. Tezgah kurulurken bir `BlockPlaceEvent` sorgulanır, yani WorldGuard ya da
claim eklentilerinin yasakladığı yere tezgah kurulamaz.

## Ayarlar

Her şey `plugins/AuraForge/config.yml` içinde: tezgah bloğu, malzeme sınırı, animasyon aralığı, dövme
zorluğu (tur sayısı, imleç hızı, bölge genişlikleri), kalite çarpanları, yetenek değerleri, set bonusu,
tarifler ve tüm mesajlar (MiniMessage). Yeni tarif eklemek için:

```yaml
recipes:
  alev-kilici:
    ability: dash
    result: GOLDEN_SWORD
    name: "<gradient:#FF9A3C:#FF4E50>Alev Kılıcı</gradient>"
    description: "<gray>İsteğe bağlı açıklama satırı."
    ingredients:
      BLAZE_ROD: 2
      GOLD_INGOT: 2
```

Malzemeler tezgahtakilerle birebir eşleşmeli ve toplamı `forge.max-items` değerini geçmemeli. Aynı
malzemeleri kullanan iki tarif tanımlanırsa ikincisi uyarıyla atlanır.

Tezgahlar ve demirci istatistikleri `plugins/AuraForge/data.json` dosyasında tutulur; değişiklik olunca
30 saniyede bir ve sunucu kapanırken yazılır. Dosya bozulursa yedeği alınıp boş başlanır.

## Diğer eklentiler için

Dövme tamamlanmadan önce `com.r0ine.auraforge.api.ForgeCraftEvent` çağrılır. İptal edilirse malzemeler
tezgahta kalır, `result` değiştirilerek farklı bir eşya verilebilir.

```kotlin
@EventHandler
fun onCraft(event: ForgeCraftEvent) {
    if (event.quality >= 90) event.player.sendMessage(Component.text("Usta elinden çıkmış!"))
}
```

## Nasıl çalışıyor

- Tezgahın görüntüsü `BlockDisplay`, `TextDisplay`, `Interaction` ve her malzeme için bir `ItemDisplay`
  varlığından oluşur. Varlıklar kalıcı değildir; chunk yüklendiğinde kurulur, boşaltıldığında kaldırılır.
  Asıl veri `data.json`'dadır, sunucu çökse bile dünyada sahipsiz varlık kalmaz.
- Süzülme animasyonu her tezgah için ayrı bir coroutine'dir ve tezgahın bulunduğu bölge thread'inde
  çalışır (`RegionDispatcher`). Display interpolasyonu kullanıldığı için birkaç tickte bir güncelleme
  akıcı görünür; yakında oyuncu yoksa güncelleme durur.
- Dövme oturumu oyuncunun thread'inde çalışan bir coroutine'dir (`EntityDispatcher`). Oyuncu çıkınca
  entity scheduler görevi emekliye ayırır ve coroutine kendiliğinden iptal olur.
- Eklenti kapanırken tek bir `CoroutineScope` iptal edilir, tüm animasyon ve oturumlar onunla biter.

## Derleme

```bash
./gradlew build
```

Çıktı `build/libs/AuraForge-<sürüm>.jar`. Kotlin ve kotlinx kütüphaneleri jar'ın içine kendi paket
adlarıyla gömülür, sunucudaki başka Kotlin eklentileriyle çakışmaz.

## Lisans

MIT
