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

## Macera menüsü (1.2)

Tezgah ve büyü masasında **+ Macera** düğmesi var. `/af adventure` da aynı menüyü açar.
Eski + Gelişim, element yuvası ve üç dallı silah ağacı çalışmaya devam eder. Yeni menüdeki
rehber; eşya üretiminden mühür, büyü ve set seçimine kadar ilerleme yolunu gösterir.

Sekiz yeni sistem birbirini besler:

1. **Maceracı ustalığı:** günlükteki bir yaratığı dövülmüş silahla öldürmek 10 XP ve 2 öz verir.
   Her 100 XP bir rütbe; üst sınır 20. rütbe. Bu ilerleme oyuncuya, silahın eski seviyesi eşyaya aittir.
2. **Silah mühürleri:** 20 özellikten seçerek silaha işle. Seviye 1'de bir, 10'da iki, 20'de üç yuva açılır.
   İşleme bedeli `12 + gereken rütbe × 4` öz. İşlenmiş mühre tekrar tıklamak onu çıkarır; bedeli iade edilmez.
3. **Set atölyesi:** beş setten birini elindeki dövülmüş zırha işle. Rütbe 3 ve parça başına 40 öz gerekir.
   Aynı işlem eski seti değiştirir. Farklı setlerden iki parça giyerek 2+2 düzeni de kurulabilir.
4. **Büyü araştırması ve mana:** aşağıdaki 10 büyü kalıcı öğrenilir. Seçili büyü, dövülmüş silahla
   eğilerek sağ tıklayınca çalışır; normal sağ tık eski silah yeteneğini kullanır. Seçili büyüye tekrar
   tıklamak seçimi kaldırır. Mana saniyede 4 yenilenir; sınır `100 + (rütbe − 1) × 2`.
5. **Av sözleşmeleri:** sekiz türden birini seç, kabulden sonra belirtilen sayıda avla ve ödülü menüden al.
   İlk görev 5 av, 15 öz ve 30 XP verir. Her teslimle hedef ve öz ödülü artar; sınır 15 av / 45 özdür.
   Aynı anda tek sözleşme tutulur; iptal edilen sözleşmenin ilerlemesi silinir.
6. **Yaratık günlüğü:** zombi, iskelet, örümcek, creeper, enderman, blaze, yağmacı ve wither iskeleti izlenir.
   Her türde 10, 30 ve 75 av eşikleri sırasıyla 10, 30 ve 75 öz verir. Alınan her eşik ödülü o türe
   kalıcı %2 ek hasar sağlar; toplam %6. Ödüller otomatik verilmez, günlükten alınır.
7. **Kombo:** aynı hedefe 4 saniye içinde yapılan dolu yakın dövüş vuruşları zincir kurar.
   Zincir 8'e kadar çıkar; ilk vuruştan sonraki her basamak %2 ek hasar verir. Hedef değiştirmek,
   süreyi aşmak veya sunucudan çıkmak zinciri sıfırlar.
8. **Söküm:** yedek dövülmüş eşyaları `4 + kalite/10 + arıtma×2` öz karşılığında tüketir.
   Bölme aşağı yuvarlanır. Eşya önizlemesi ve ayrı onay düğmesi vardır; onay sırasında eldeki eşya
   değişmişse işlem reddedilir. Eşya üzerindeki seviye, element ve mühürler geri verilmez.

Spawner, spawn yumurtası ve dispenser yumurtası kaynaklı yaratıklar yeni maceracı/av/günlük ödüllerine
sayılmaz. Hayvanlar, oyuncular ve zırh askıları bu av ekonomisinin dışındadır. Mühürlerin vuruş etkileri
yalnızca en az %85 dolmuş doğrudan yakın dövüş saldırılarında çalışır; süpürme saldırısı zincir üretmez.

### 20 silah özelliği

| Mühür | Rütbe | Etki |
|---|---:|---|
| Kor İzi | 1 | 3 saniye yakma |
| Kırağı | 1 | 2 saniye Yavaşlık I |
| Zehir Dişi | 2 | Bağışık olmayan hedefe 3 saniye Zehir I |
| Yırtılma | 3 | 2 saniye Wither I |
| Son Hüküm | 4 | Canı %30 altında hedefe %20 hasar |
| İlk Yara | 2 | Canı %90 üzerinde hedefe %20 hasar |
| Dev Avcısı | 5 | Azami canı en az 40 olan hedefe %20 hasar |
| Zırh Çatlağı | 4 | Zırh puanı başına %1 hasar, en fazla %20 |
| Kan Bağı | 5 | Son hasarın %6'sı kadar iyileşme, vuruş başına en fazla 1 can |
| Mana Sızıntısı | 2 | Vuruşta 3 mana |
| İvme | 3 | Kombo başına %2 hasar, en fazla %16 |
| Sırt Darbesi | 5 | Arkadan saldırıda %25 hasar |
| Hava Avcısı | 4 | Yere basmayan hedefe %15 hasar |
| Buz Kıran | 6 | Donmuş veya yavaş hedefe %18 hasar |
| Köz Besleyen | 6 | Yanan hedefe %18 hasar |
| Savuruş | 2 | Hedefi geri itme |
| Ağırlık | 7 | Yatay hızı kesme ve yere bastırma |
| Avcı Mührü | 3 | Günlükteki yaratık türlerine %12 hasar |
| Odak | 7 | En az %80 manayla %12 hasar |
| Ruh Hasadı | 8 | Uygun av öldürmelerinde fazladan 2 öz |

### Büyü ağacı

Araştırma bedeli `15 + gereken rütbe × 8` öz. Öğrenilen büyü tekrar satın alınmaz.
Kök büyüler rütbe ve özle açılır; alt büyüler ayrıca öncülünü ister. Her kullanım kendi beklemesine
ek olarak 1 saniyelik ortak bekleme başlatır. Çıkıp girmek beklemeleri silmez.

| Büyü | Rütbe | Öncül | Mana / bekleme | Etki |
|---|---:|---|---|---|
| Kor Mızrağı | 1 | Kök | 18 / 6 sn | 18 blokta 6 hasar, 4 sn yanma |
| Buz Hapishanesi | 2 | Kök | 22 / 9 sn | 16 blokta 4 hasar, 3 sn Yavaşlık IV |
| Şimşek Örgüsü | 3 | Kök | 30 / 12 sn | 8 blokta en yakın 4 görünür hedefe 5 hasar |
| Taş Deri | 2 | Kök | 22 / 14 sn | 6 sn Direnç II |
| Yaşam Filizi | 3 | Kök | 30 / 16 sn | 6 can, 4 sn Yenilenme I |
| Boşluk Kancası | 4 | Kor Mızrağı | 26 / 10 sn | 16 blokta 4 hasar, hedefi çekme |
| Güneş Tacı | 6 | Kor Mızrağı | 40 / 18 sn | 6 blokta en çok 8 hedefe 7 hasar, kendi yanmanı söndürme |
| Kristal Kırımı | 5 | Buz Hapishanesi | 32 / 12 sn | 7 blokta 5 hasar; donmuş/yavaş hedefe 9 |
| Rüzgâr İzleri | 4 | Şimşek Örgüsü | 20 / 8 sn | İleri atılma, 5 sn Yavaş Düşüş ve Hız II |
| Tutulma | 8 | Boşluk Kancası | 45 / 20 sn | 18 blokta 8 hasar; hedef %30 candan düşükse 12 |

Hedefli büyüler duvar arkasını seçmez; alan büyüleri görüş hattını kontrol eder. Hedef bulunamazsa
mana harcanmaz. Başlatılmış büyünün hasarı başka eklenti tarafından engellenirse mana ve bekleme
harcanmış kalır, hedefe ek yanma/çekme gibi etkiler uygulanmaz. PvP kapalı dünyada oyuncular,
dost ateşi kapalı aynı takımdakiler ve kendi evcil hayvanların hedef alınmaz.

### Setler

Her set miğfer, göğüslük, pantolon ve bot üzerinden dört parçaya tamamlanır. Mevcut dövme tarifleri
bu dört ekipman türünü sağlar. Bonuslar eşya elde tutulunca değil, zırh yuvasına giyilince çalışır.

| Set | 2 parça | 4 parça |
|---|---|---|
| Kül Muhafızı | Yanan hedefe %10 hasar | Ateş hasarı %35 azalır; vuruşlar yakar |
| Buzul Nöbetçisi | Yakın dövüş hasarı %6 azalır | Vuran düşmana 2 sn Yavaşlık II |
| Fırtına Gezgini | Kombo penceresi 6 sn olur | Tam vuruşta 2 sn Hız I |
| Kök Bekçisi | Yakın dövüş, mermi ve büyü hasarı %8 azalır | Kombo 5'e ulaşınca 1 can |
| Yıldız Dokumacı | Mana sınırına +20 | Büyülerin mana bedeli %20 azalır, yukarı yuvarlanır |

Yeni oyuncu ilerlemesi, araştırmalar, sözleşmeler, mana ve beklemeler `data.json` içindeki
`adventurers` alanında UUID ile saklanır. Eski kayıt dosyaları bu alan olmadan da açılır.
Mühür ve setler eşyanın PDC verisine yazılır; eşya devredilince bunlar da devredilir.
Yeni içerik dengeleri `adventure/AdventureCatalog.kt` ve ilgili Kotlin servislerinde tanımlıdır.

## Komutlar ve yetkiler

| Komut | Yetki | Ne yapar |
|---|---|---|
| `/af adventure` | `auraforge.use` | Macera menüsü, mühürler, büyü araştırması, setler ve av günlüğü |
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
`auraforge.cast` araştırılmış büyüleri ve eski parşömenleri kullanma yetkisidir; varsayılan olarak açıktır.

## Ayarlar

`plugins/AuraForge/config.yml` içinde: tezgah bloğu, malzeme sınırı, animasyon aralığı, dövme
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

### Doğrulama

`./gradlew test` rütbe, araştırma önkoşulları, mana, mühür yuvaları ve kayıt uyumluluğu testlerini çalıştırır.
Gerçek Paper kontrolü için `./gradlew build integrationJar` ile ayrıca `AuraForgeChecks.jar` üretilebilir.
Bu kontrol eklentisi teslim jar'ına dahil değildir ve oyuncuların bulunduğu sunucuya kurulmaz.

`scripts/integration-check.py`, verilen Paper 1.21.4 jar'ıyla `build` altında ayrı bir sunucu açar.
Java 21, Paper jar'ı ve Mineflayer kurulu bir dizin gerekir. Örneğin:

```powershell
python scripts/integration-check.py --paper test-server/paper-1.21.4-232.jar --mineflayer ../mineflayer-test/node_modules/mineflayer --java 'C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin/java.exe'
```

Varsayılan test portu 25586'dır; doluysa araç başlamadan durur. Test oyuncusu menü, savaş, araştırma,
set, ödül ve söküm kontrollerine bağlanır. Araç sunucuyu kapatıp yeniden başlatarak kayıtları da sınar.
Loglar oluşturulan `build/integration-server-<zaman>/phase-1.log` ve `phase-2.log` dosyalarında kalır.
Canlı sunucuya sürüm geçirmek için eski jar'ın yedeğini al, sunucuyu kapat, `AuraForge-1.2.0.jar`
dosyasını `plugins` içine yerleştir ve yeniden başlat; iki AuraForge jar'ını aynı anda bırakma.

## Lisans

MIT
