# AuraForge

Paper ve Folia için Kotlin ile yazılmış bir dövme tezgahı + büyü masası + silah gelişimi eklentisi. Oyuncu dünyaya bir
tezgah kurar, malzemeleri üstüne koyar, malzemeler tezgahın üstünde dönmeye başlar. Dövme sırasında
ekranda kayan bir imleç çıkar; doğru anda vuran oyuncu daha kaliteli bir eşya alır. Çıkan silah ve
zırhların sağ tık ya da eğilip zıplamayla tetiklenen yetenekleri vardır. İkinci bir masa daha var:
büyü masası. İki rune'u birleştirince element kombinasyonuna göre parşömen çıkıyor — ateş + rüzgâr
ateşli kasırga veriyor, rüzgâr + rüzgâr dalga kalkanı, ateş + şimşek plazma mızrağı, böyle 12 tane.

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
| Kalkan Dalgası (`wave`) | Block display halkası dışa doğru genişler, yakalanan canlılar uçar |

Silah ve kalkanlar sağ tıkla, zırhlar giyiliyken **eğilip zıplayınca** çalışır. Kalite gücü artırır ve
bekleme süresini kısaltır. Aynı yeteneği taşıyan zırh parçaları (varsayılan 2 ve üzeri) set bonusu verir.

## Büyü masası

Bir **Büyü Çekirdeği** (`/af altar`, veya `lapis + amethyst + echo_shard` tarifi) bir bloğa sağ
tıklayınca bir büyü masası oluşturur. Masaya sağ tıklayarak iki yuvalı GUI açılır:

1. İki yuvaya birer **rune** koy (`/af rune fire | wind | ice | thunder | earth`).
2. Masa iki rune'u tanıyınca sonuç yuvası eşleşen parşömeni önizler.
3. "Birleştir"e bas, karşılığında büyü parşömenini al.
4. Parşömene sağ tıklayarak büyüyü kullan (parşömen tek kullanımlıktır).

12 kombinasyon:

| Elementler | Büyü | Efekt |
|---|---|---|
| Ateş + Rüzgâr | Ateşli Kasırga | Önünde yürüyen alev sütunu, düşmanları çeker ve yakar |
| Rüzgâr + Rüzgâr | Dalgalar Rüzgârı | Genişleyen cam halkası, çevredekileri savurur |
| Ateş + Şimşek | Plazma Mızrağı | Baktığın yönde çizgisel bir hüzme |
| Ateş + Toprak | Magma Sütunu | Hedef noktada yükselen magma kulesi |
| Buz + Toprak | Buzul Duvarı | Önüne 5x3 buz duvarı diker, dokunanı dondurur |
| Rüzgâr + Şimşek | Gök Gürültüsü | Hedef noktaya ışınlan + yıldırım halkası |
| Rüzgâr + Buz | Kar Fırtınası | Önüne kar konisi püskürtür |
| Ateş + Ateş | Cehennem Alanı | Çevreni alevli bir arenaya çevirir |
| Şimşek + Şimşek | Fırtına Zinciri | En yakın düşmana sıçrayan yıldırım |
| Toprak + Toprak | Taş Yumruk | Dönerek giden taş topu fırlatır |
| Ateş + Buz | Buhar Patlaması | Radyal buhar itmesi |
| Rüzgâr + Toprak | Toz Sarmalı | Hedef noktada düşmanları kendine çeken hortum |

Her parşömen 1-10 arası bir **güç** değeriyle çıkar; güç yüksekse büyünün alanı, menzili ve süresi
artar, bekleme süresi düşer. Aynı iki rune kullanılırsa güç biraz daha yüksek başlar.

## Gelişim: seviye, ustalık ağacı, arıtma

Dövülen silahlar kullanıldıkça büyür. Vuruşlar ve öldürmeler silaha XP kazandırır (öldürülen canlının
canı kadar), her seviye bir **ustalık puanı** verir; sınır 30. seviye. Seviye atlayınca silah oyuncunun
başının üstünde dönerek yükselir.

Büyü masasının menüsünde **+ Gelişim** düğmesi var. Elindeki dövülmüş eşyayla açınca:

- **Arıtma (+)**: eşyayı +1'den +10'a çıkarır. Her basamağın şansı ve bedeli ayrı (demirden başlayıp
  netherite külçesine kadar). +6'dan sonra başarısız deneme eşyayı bir basamak düşürür, +8 ve üstü
  sunucuya duyurulur. Masanın üstüne bir örs iner; başarıda altın, başarısızlıkta kömür kıymıkları saçılır.
  Silahta her + hasarı, her eşyada yetenek gücünü, zırhta alınan hasarı azaltmayı artırır.
- **Element yuvası**: envanterden bir rune'a shift+tık yapınca rune silaha işlenir. Vuruşlarda element
  tetiklenir: ateş tutuşturur, rüzgâr savurur, buz dondurur, şimşek yıldırım düşürür, toprak sarsar.
- **Ustalık ağacı**: üç dal, her dalda beş basamak. 2. ve 4. basamaklar yol ayrımıdır, ikisinden biri
  seçilince diğeri kapanır. Alt basamaklar için önce üsttekini açmak ve o dalda yeterli puan harcamak
  gerekir. Sıfırlamak 10 XP seviyesine mal olur.

| Dal | Basamaklar |
|---|---|
| Güç Yolu | Keskin Ağız → Ağır Darbe / Kanatma → İnfaz → Kritik Vuruş / Can Emme → **Öfke** |
| Rüzgâr Yolu | Tempo → Av Coşkusu / Dalga Gücü → Yankı → Sıyrılma / Kinetik → **Fırtına Adımı** |
| Element Yolu | Rezonans → Yoğunluk / Element Zırhı → Seken Element → Aşırı Yük / Element Emme → **Element Avatarı** |

Element Avatarı her 12. element tetiklemesinde silahın elementinin çift büyüsünü (ateş → Cehennem Alanı,
şimşek → Fırtına Zinciri...) kendiliğinden patlatır.

**Kodeks** (`/af codex`, ya da masadaki kitap) oyun içi wiki'dir: elementler, 12 büyü, dövme tarifleri,
yetenekler, ağacın bütün düğümleri ve arıtma tablosu sayfa sayfa gezilir. Zırhlar yalnızca arıtılır,
seviye ve ağaç silahlara özeldir.

### Block display animasyonları

Büyülerin görsel kısmı Minecraft 1.21 block display varlıklarıyla yapılır: her tick transformasyon
interpolasyonuyla dönme, ölçekleme ve taşıma. Parçacık efekti değil tam blok görseli — Buzul Duvarı
gerçek buz blokları, Magma Sütunu yerden çıkan magma, Taş Yumruk dönen taş bloğu. Tezgah ve masanın
kendi görselleri de aynı sistemle çalışır. Tüm entity'ler persistent değildir; chunk boşalınca kaybolur,
yüklenince yönetici geri kurar.

## Komutlar ve yetkiler

| Komut | Yetki | Ne yapar |
|---|---|---|
| `/af recipes` | `auraforge.use` | Dövme tariflerini listeler |
| `/af spells` | `auraforge.use` | Büyü kombinasyonlarını listeler |
| `/af elements` | `auraforge.use` | Elementleri ve rune malzemelerini listeler |
| `/af list` | `auraforge.use` | Tezgahlarının ve masalarının konumu |
| `/af tree` | `auraforge.use` | Elindeki silahın ustalık ağacı |
| `/af codex` | `auraforge.use` | Kodeks (oyun içi wiki) |
| `/af set <level\|plus> <değer>` | `auraforge.give` | Elindeki eşyanın seviyesini ya da arıtmasını ayarlar |
| `/af stats [oyuncu]` | `auraforge.use` | Dövme istatistikleri |
| `/af give [oyuncu] [adet]` | `auraforge.give` | Forge Çekirdeği verir |
| `/af altar [oyuncu] [adet]` | `auraforge.give` | Büyü Çekirdeği verir |
| `/af rune <element> [adet] [oyuncu]` | `auraforge.give` | Rune verir |
| `/af item <tarif> [kalite] [oyuncu]` | `auraforge.give` | Hazır yetenekli eşya verir |
| `/af scroll <büyü> [güç] [oyuncu]` | `auraforge.give` | Hazır parşömen verir |
| `/af reload` | `auraforge.reload` | config.yml'yi yeniden yükler |

`auraforge.place` tezgah kurmayı, `auraforge.admin` başkasının tezgahını kullanmayı ve oyuncu başına
tezgah sınırını aşmayı sağlar. Tezgah kurulurken bir `BlockPlaceEvent` sorgulanır, yani WorldGuard ya da
claim eklentilerinin yasakladığı yere tezgah kurulamaz.

## Ayarlar

Her şey `plugins/AuraForge/config.yml` içinde: tezgah bloğu, malzeme sınırı, animasyon aralığı, dövme
zorluğu (tur sayısı, imleç hızı, bölge genişlikleri), kalite çarpanları, yetenek değerleri, set bonusu,
silah seviyesi (`progression`), arıtma şans ve bedelleri (`enhance`), tarifler ve tüm mesajlar (MiniMessage). Yeni tarif eklemek için:

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
