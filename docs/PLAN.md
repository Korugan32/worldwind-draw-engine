# WorldWind Draw Engine — Detaylı Proje Planı

> Hedef: WorldWind Kotlin 2.0.5 üzerine kurulu, KMP uyumlu, temiz mimarili bir çizim/düzenleme motoru.
> Bu doküman, mimari kararları + fazlı geliştirme planını tek yerde toplar. Geliştirme ilerledikçe güncellenmesi beklenen **yaşayan bir belge**dir.

---

## 1. Vizyon ve Kapsam

`worldwind-draw-engine`, WorldWind Kotlin (WWK) üzerinde:
- Pan / Draw / Edit modlarını tek bir reaktif state machine üzerinden yöneten,
- Command pattern ile milisaniyeler içinde undo/redo yapabilen,
- Poligon/polyline/marker vertex'lerini seçip sürükleyebilen, araya nokta ekleyebilen,
- Renk yanında resim/pattern dolgu, tema ve animasyon (su maskeleme dahil) destekleyen,
- Host uygulamanın (özellikle Compose Multiplatform) frame-clock'una dışarıdan beslenen, kendi başına thread/timer açmayan

bir çizim motorudur. Kütüphane, WWK'nin platform sınırlarından etkilenmeyecek şekilde ikiye bölünmüştür (`core` / `wwk-adapter`).

> **Güncelleme (Faz 0 doğrulaması, 2026-07-15):** Bu doküman ilk yazıldığında "WWK şu an yalnızca Android + Web'de production-ready, iOS desteği yok" varsayımıyla hazırlanmıştı. `earth.worldwind:worldwind:2.0.5`'in Gradle module metadata'sı doğrudan incelenerek bunun **artık doğru olmadığı** doğrulandı: paket fiilen `android`, `iosArm64`, `iosSimulatorArm64`, `js`, `jvm`, `wasmJs` variant'larını yayınlıyor (iOS desteği v1.14.0'da, wasmJs desteği v2.0.1'de eklenmiş — ikisi de 2.0.5'ten önce). Detay için bkz. §8 Karar #4 ve §9 Risk tablosu.

---

## 2. Mimari Prensipler (özet)

1. **Core / Adapter ayrımı sert bir kuraldır.** `core` modülü WWK'nin tek bir sınıfını dahi import etmez.
2. **Tek yönlü veri dönüşümü sınırı:** WWK tipleri (`Position`, `Sector`, `ShapeAttributes`, `Polygon` vb.) yalnızca adapter modülünün mapper sınıflarında yaşar, oradan öteye sızmaz.
3. **Kütüphane kendi clock/thread'ini açmaz.** Animasyon `tick(deltaTime)` ile dışarıdan beslenir; frame kaynağı (Compose `withFrameNanos`, Choreographer, rAF, Swing Timer...) her zaman host uygulamanın sorumluluğundadır.
4. **State machine saftır.** `(State, Event) -> State` fonksiyonları yan etkisiz; yan etkiler (WWK'ye yazma, redraw isteme) yalnızca adapter/facade katmanında olur.
5. **Command'lar delta taşır, tam obje kopyası değil** (özellikle çok vertex'li poligonlarda bellek/performans için).
6. **Public API tek kapıdan geçer:** `WorldWindDrawEngine` facade dışında hiçbir sınıf `public` değildir (mümkün olduğunca `internal`).

---

## 3. Modül Yapısı

İki ayrı Gradle modülü, ayrı yayınlanabilir (veya composite build ile birlikte kullanılabilir) iki KMP kütüphane:

```
worldwind-draw-engine/                        (repo kökü, çok-modüllü)
├── settings.gradle.kts                       // include(":core"), include(":wwk-adapter")
├── build.gradle.kts                          // ortak konfigürasyon (versiyon, publish vs.)
│
├── core/                                     ← WWK'ye SIFIR bağımlılık
│   ├── build.gradle.kts                      // target: android, jvm, js, wasmJs, iosX64/iosArm64/iosSimulatorArm64, (istenirse macos/linux/mingw)
│   └── src/
│       ├── commonMain/kotlin/com/tcsappdev/worldwinddraw/core/
│       │   ├── geometry/
│       │   │   ├── GeoPosition.kt            // kendi lat/lon/alt value class'ımız
│       │   │   ├── Vertex.kt                 // id + GeoPosition + metadata
│       │   │   ├── DrawableEntity.kt         // sealed interface: DrawnPolygon, DrawnPolyline, DrawnMarker
│       │   │   ├── DrawnPolygon.kt
│       │   │   ├── DrawnPolyline.kt
│       │   │   ├── DrawnMarker.kt
│       │   │   └── GeometryOps.kt            // saf matematik: nearest-edge, point-in-polygon, insert-at-edge
│       │   │
│       │   ├── style/
│       │   │   ├── ShapeStyle.kt             // interior: SolidColor | Gradient | ImagePattern | AnimatedFill
│       │   │   ├── StyleState.kt             // Normal / Hover / Selected / Editing / Disabled
│       │   │   ├── Theme.kt
│       │   │   └── StyleResolver.kt          // (entity, state, theme) -> ShapeStyle
│       │   │
│       │   ├── animation/
│       │   │   ├── Animatable.kt             // interface: onTick(deltaMs)
│       │   │   ├── AnimatedProperty.kt       // T başlangıç/hedef + interpolator
│       │   │   ├── AnimationSpec.kt          // duration, easing, repeatMode
│       │   │   ├── AnimationController.kt    // registry: tick(deltaMs) dışarıdan çağrılır, kendi clock'u YOK
│       │   │   └── RedrawSignal.kt           // Flow<Unit> — "bir şey değişti, yeniden çiz" sinyali
│       │   │
│       │   ├── state/
│       │   │   ├── DrawMode.kt               // sealed: Idle, Pan, Draw, Edit, Select
│       │   │   ├── DrawEvent.kt              // sealed: Click, DragStart, DragMove, DragEnd, KeyPress, ModeChange...
│       │   │   ├── DrawEngineState.kt        // immutable data class snapshot
│       │   │   ├── DrawEngineReducer.kt      // (state, event) -> (state, List<Command>)
│       │   │   └── DrawStateMachine.kt       // StateFlow<DrawEngineState> sahibi
│       │   │
│       │   ├── history/
│       │   │   ├── Command.kt                // execute() / undo(), delta tabanlı
│       │   │   ├── commands/
│       │   │   │   ├── AddEntityCommand.kt
│       │   │   │   ├── DeleteEntityCommand.kt
│       │   │   │   ├── AddVertexCommand.kt
│       │   │   │   ├── MoveVertexCommand.kt
│       │   │   │   ├── RemoveVertexCommand.kt
│       │   │   │   └── ChangeStyleCommand.kt
│       │   │   ├── CompositeCommand.kt       // sürükleme sırasında ara adımları tek undo'ya sıkıştırma
│       │   │   └── HistoryManager.kt         // undo/redo stack + canUndo/canRedo Flow'ları
│       │   │
│       │   ├── editing/
│       │   │   ├── VertexEditController.kt   // seçim + sürükleme + edge'e nokta ekleme mantığı (saf, koordinat-agnostik)
│       │   │   ├── HitTestService.kt         // interface — gerçek pick işini adapter yapar, burada sadece sözleşme
│       │   │   └── SnappingStrategy.kt       // opsiyonel: grid/vertex/edge snapping
│       │   │
│       │   ├── repository/
│       │   │   └── EntityRepository.kt       // aktif entity'lerin in-memory, StateFlow tabanlı kaynağı
│       │   │
│       │   └── engine/
│       │       └── DrawEngineCore.kt         // core'un kendi iç facade'i (henüz WWK yok), wwk-adapter modülü bunu sarar
│       │
│       └── commonTest/kotlin/.../            // domain + state + history + geometry testleri (WWK'siz, hızlı)
│
├── wwk-adapter/                              ← WWK'ye bağımlı, sadece WWK'nin desteklediği target'lar
│   ├── build.gradle.kts                      // target: android, jvm, js, wasmJs, iosArm64, iosSimulatorArm64 (earth.worldwind:worldwind'in yayınladığı variant'lar)
│   │                                          // dependency: core (implementation/api), earth.worldwind:worldwind
│   └── src/
│       └── commonMain/kotlin/com/tcsappdev/worldwinddraw/adapter/
│           ├── mapper/
│           │   ├── GeoPositionMapper.kt      // core.GeoPosition <-> wwk.Position (iki yönlü)
│           │   ├── StyleAttributesMapper.kt  // core.ShapeStyle <-> wwk.ShapeAttributes/PlacemarkAttributes
│           │   └── SectorMapper.kt           // gerekirse core.BoundingBox <-> wwk.Sector
│           │
│           ├── render/
│           │   ├── PolygonRenderAdapter.kt   // DrawnPolygon -> wwk.shape.Polygon senkronizasyonu
│           │   ├── PolylineRenderAdapter.kt  // -> wwk.shape.Path
│           │   ├── MarkerRenderAdapter.kt    // -> wwk.shape.Placemark
│           │   └── DrawLayer.kt              // wwk.layer.RenderableLayer sarmalayıcı, EntityRepository'yi dinler
│           │
│           ├── input/
│           │   └── PickInputBridge.kt        // WWK pick/gesture sonucu -> core.DrawEvent çevirisi (TEK giriş noktası)
│           │
│           └── engine/
│               └── WorldWindDrawEngine.kt    ← PUBLIC FACADE (dışarıya açılan tek sınıf)
│
└── docs/
    └── (Dokka ile üretilecek KDoc çıktısı için config)
```

---

## 4. Paket Bazında Sorumluluk Tablosu

| Paket | Modül | WWK'ye bağımlı mı? | Sorumluluk |
|---|---|---|---|
| `core.geometry` | core | ❌ | Saf veri modelleri + geometri matematiği |
| `core.style` | core | ❌ | Stil modelleri, tema, çözümleme mantığı |
| `core.animation` | core | ❌ | `tick()` ile beslenen animasyon motoru, clock'suz |
| `core.state` | core | ❌ | State machine, reducer |
| `core.history` | core | ❌ | Command pattern, undo/redo stack |
| `core.editing` | core | ❌ | Vertex düzenleme mantığı (koordinat-agnostik) |
| `core.repository` | core | ❌ | In-memory entity kaynağı |
| `adapter.mapper` | wwk-adapter | ✅ | core ↔ WWK tip dönüşümü (TEK dönüşüm noktası) |
| `adapter.render` | wwk-adapter | ✅ | core modellerini WWK Renderable'larına yazma |
| `adapter.input` | wwk-adapter | ✅ | WWK input/pick olaylarını `DrawEvent`'e çevirme |
| `adapter.engine` | wwk-adapter | ✅ | Public Facade — her şeyi birbirine bağlar |

---

## 5. Fazlı Geliştirme Planı

### Faz 0 — İskelet ✅ Tamamlandı (2026-07-15)
- [x] Repo kökü, çok-modüllü `settings.gradle.kts`
- [x] `core` ve `wwk-adapter` modüllerinin boş KMP iskeletleri, target tanımları
- [x] Versiyon kataloğu (`libs.versions.toml`), ortak Gradle konvansiyonları
- [x] Dokka kurulumu (KDoc çıktısı için, boş da olsa pipeline hazır olsun)
- **Çıktı:** `./gradlew build` iki modülde de yeşil, hiç iş mantığı yok.
- **Notlar:**
  - Kök paket adı `com.tcsappdev.worldwinddraw.*` olarak belirlendi (bkz. §8 Karar #5).
  - `wwk-adapter` hedefleri: `android`, `jvm`, `js`, `wasmJs`, `iosArm64`, `iosSimulatorArm64` (bkz. §8 Karar #4 — WWK'nin gerçekten yayınladığı variant'lar doğrulandı).
  - `settings.gradle.kts`'te `repositoriesMode` `FAIL_ON_PROJECT_REPOS` yerine `PREFER_PROJECT` yapıldı (Kotlin/JS'in otomatik Node.js indirme repository'siyle çakışıyordu).
  - `wwk-adapter` `compileSdk` 37'ye çıkarıldı (`earth.worldwind:worldwind:2.0.5` bunu zorunlu kılıyor).

### Faz 1 — Core Domain Modelleri
- [ ] `GeoPosition`, `Vertex`, `DrawableEntity` sealed hiyerarşisi
- [ ] `ShapeStyle`, `StyleState`, `Theme` modelleri (henüz resolver yok)
- [ ] `GeometryOps`: point-in-polygon, nearest-edge, edge-insert saf fonksiyonları
- [ ] commonTest: geometri fonksiyonları için tablo-tabanlı (parametrize) testler
- **Bağımlılık:** Faz 0
- **Çıktı:** WWK'siz, %100 test edilebilir domain katmanı.

### Faz 2 — Pure State Machine
- [ ] `DrawMode`, `DrawEvent` sealed tipleri
- [ ] `DrawEngineState` immutable snapshot
- [ ] `DrawEngineReducer`: `(state, event) -> (newState, sideEffects: List<Command>)`
- [ ] `DrawStateMachine`: `StateFlow<DrawEngineState>` + event giriş noktası
- [ ] commonTest: her mode geçişi ve event kombinasyonu için reducer testleri
- **Bağımlılık:** Faz 1
- **Çıktı:** UI'sız, WWK'siz, tamamen deterministik state machine.

### Faz 3 — History / Undo-Redo
- [ ] `Command` interface + delta tabanlı somut komutlar (`AddEntityCommand`, `MoveVertexCommand` vb.)
- [ ] `HistoryManager`: undo/redo stack, kapasite limiti, `canUndo`/`canRedo` Flow'ları
- [ ] `CompositeCommand`: sürükleme sırasında üretilen ardışık ara-komutları tek undo adımına birleştirme (debounce/coalescing stratejisi)
- [ ] Reducer'ın ürettiği `Command`'ların `HistoryManager`'a otomatik push edilmesi
- [ ] commonTest: undo/redo zinciri, composite coalescing senaryoları
- **Bağımlılık:** Faz 1, 2
- **Çıktı:** State machine + history birlikte, hâlâ WWK'siz test edilebilir.

### Faz 4 — Animasyon Sözleşmesi (henüz görsel değil)
- [ ] `Animatable`, `AnimatedProperty<T>`, `AnimationSpec`
- [ ] `AnimationController.tick(deltaMs)` — dışarıdan beslenen, clock'suz
- [ ] `RedrawSignal`: `Flow<Unit>` — host'un ne zaman redraw isteyeceğini bilmesi için sinyal
- [ ] commonTest: interpolasyon doğruluğu, easing fonksiyonları, tick sırası
- **Bağımlılık:** Faz 1
- **Çıktı:** Animasyon motoru mantığı hazır, henüz hiçbir görsel çıktı yok (görsel bağlama Faz 8'de).

### Faz 5 — WWK Adapter: Statik Render (henüz etkileşimsiz)
- [ ] `GeoPositionMapper`, `StyleAttributesMapper` (iki yönlü, birim testli — JVM target'ta WWK ile test edilebilir)
- [ ] `PolygonRenderAdapter`, `PolylineRenderAdapter`, `MarkerRenderAdapter`: `EntityRepository`'yi dinleyip WWK shape'lerini senkron tutma
- [ ] `DrawLayer`: `RenderableLayer` sarmalayıcı
- **Bağımlılık:** Faz 1, 3 (repository state'ini okuyacak)
- **Çıktı:** `EntityRepository`'ye elle bir `DrawnPolygon` eklenince ekranda (statik) görünüyor. Henüz tıklama/sürükleme yok.

### Faz 6 — Input / Pick Köprüsü
- [ ] `PickInputBridge`: WWK pick sonucu + gesture → `DrawEvent` çevirisi
- [ ] `HitTestService`'in gerçek implementasyonu (adapter tarafında, WWK pick API'siyle)
- **Bağımlılık:** Faz 2, 5
- **Çıktı:** Ekranda tıklama/sürükleme artık state machine'e ulaşıyor, mod geçişleri (Pan/Draw/Edit) gerçek girdiyle çalışıyor.

### Faz 7 — Vertex Editing (uçtan uca)
- [ ] `VertexEditController`'ın adapter ile tam bağlanması: seç → sürükle → bırak → `MoveVertexCommand`
- [ ] Edge'e tıklayıp yeni nokta ekleme akışı
- [ ] Snapping (varsa) devreye alma
- **Bağımlılık:** Faz 3, 6
- **Çıktı:** Bir poligonun köşesini sürükleyip undo/redo ile geri alabiliyoruz — bu, kütüphanenin "temel vaadi" tamamlanmış demek.

### Faz 8 — Styling + Animasyonun Görsel Bağlanması
- [ ] `StyleResolver` + `StyleAttributesMapper` tam entegrasyonu: `SolidColor` / `Gradient` / `ImagePattern` gerçek WWK `interiorImageSource`'a yazılıyor
- [ ] `StyleState` (hover/selected/editing) → görsel geri bildirim
- [ ] `AnimationController` çıktısının `StyleAttributesMapper`'a periyodik yazılması (ör. dolgu offset'i kayan su efekti v1)
- [ ] Host tarafına örnek entegrasyon: Compose'da `LaunchedEffect { while(true) withFrameNanos { controller.tick(it) } }` deseni dokümante edilir
- **Bağımlılık:** Faz 4, 5, 7
- **Çıktı:** Resim dolgulu poligonlar, seçili/hover stilleri, temel su-maskeleme animasyonu host uygulamada canlı çalışıyor.

### Faz 9 — Public Facade Sağlamlaştırma + KDoc
- [ ] `WorldWindDrawEngine` facade: `attachTo(worldWindow)`, `state`, `history`, `startDrawing(type)`, `enterEditMode(id)`, `animationController` expose'ları
- [ ] İç katmanların `internal` görünürlüğe çekilmesi (public API sızıntısı taraması)
- [ ] Tüm public API için KDoc (`@param`, `@return`, kısa kullanım örneği)
- [ ] Dokka çıktısının gözden geçirilmesi
- **Bağımlılık:** Tüm önceki fazlar
- **Çıktı:** Kütüphane dışarıdan "tek sınıf öğren, kullan" seviyesinde.

### Faz 10 — Gerçek Uygulama Entegrasyonu ve Doğrulama
- [ ] Entegrasyon yöntemi kararı (composite build / mavenLocal publish — bkz. Açık Kararlar §8)
- [ ] CMP app'e bağlanma (Android + Web + iOS hedefleri; iOS artık WWK tarafından da destekleniyor — bkz. §8 Karar #4)
- [ ] Gerçek kullanım senaryolarıyla manuel/görsel doğrulama
- [ ] commonTest suite'inin CI'da (GitHub Actions vb.) otomatik çalıştırılması
- **Bağımlılık:** Faz 9
- **Çıktı:** Kütüphane gerçek bir uygulamada kanıtlanmış durumda.

---

## 6. Cross-Module Sözleşmeler

- **Mapper kuralı:** `adapter.mapper` paketi dışında hiçbir yerde `earth.worldwind.geom.*` veya `earth.worldwind.shape.*` importu görülmemeli. Bu, code review'da otomatik grep ile denetlenebilir (`grep -r "import earth.worldwind" core/`  boş dönmeli).
- **Animasyon kuralı:** `core.animation` paketinde `Thread`, `Timer`, `delay()` içeren bir sonsuz döngü, `Choreographer`, `requestAnimationFrame` gibi platforma özgü hiçbir çağrı olmamalı — sadece `tick(deltaMs)` çağrılabilir bir yüzey.
- **Command kuralı:** Her `Command`, `undo()` çağrıldığında `execute()` öncesi duruma bit-bit dönebilmeli; bu commonTest'te "execute → undo → state eşitliği" testiyle her komut için zorunlu tutulmalı.

---

## 7. Test Stratejisi

| Katman | Test türü | Nerede çalışır | Ne zaman çalıştırılır |
|---|---|---|---|
| `core.geometry/style/animation/state/history` | Unit test (saf Kotlin) | JVM (commonTest → jvmTest) | Her commit, CI'da saniyeler içinde |
| `wwk-adapter.mapper` | Unit test (dönüşüm doğruluğu) | JVM | Her commit |
| `wwk-adapter.render/input` | Manuel/görsel doğrulama | Gerçek CMP app (submodule) | Özellik tamamlandığında |
| Uçtan uca senaryo (çiz → düzenle → undo) | Manuel/görsel doğrulama | Gerçek CMP app (submodule) | Faz 7, 8, 10'da |

---

## 8. Açık Kararlar (Decision Log)

| # | Konu | Durum | Not |
|---|---|---|---|
| 1 | Composite build mi, mavenLocal publish mi? | **Kararsız** | Geliştirme hızı için composite build önerildi, kesinleşmedi. |
| 2 | `DrawableEntity` sealed mi, registry (açık) mı? | **Kararsız** | Yeni shape tipleri (ör. `DrawnCircle`) eklenmesi kolaylaşsın isteniyorsa registry'e geçiş değerlendirilecek. |
| 3 | Su maskeleme — pattern-offset mi, custom shader mı? | **v1: pattern-offset kararlı** | Custom shader (KGL seviyesi) ileride ayrı `-fx` modülü olarak değerlendirilecek, bu plana dahil değil. |
| 4 | iOS için `core`'un ve `wwk-adapter`'ın gerçekten derlenip publish edileceği target listesi | **Çözüldü (2026-07-15)** | `earth.worldwind:worldwind:2.0.5`'in Gradle module metadata'sı incelendi: fiilen yayınlanan variant'lar `android`, `iosArm64`, `iosSimulatorArm64`, `js`, `jvm`, `wasmJs` (iosX64 yok, Intel simulator upstream'de düşürülmüş). `core` WWK'ye bağımlı olmadığı için ek olarak `iosX64` ve diğer native hedefleri de destekleyebilir; `wwk-adapter` yalnızca WWK'nin yayınladığı hedeflerle sınırlı: `android`, `jvm`, `js`, `wasmJs`, `iosArm64`, `iosSimulatorArm64`. |
| 5 | Kök paket adı | **Çözüldü (2026-07-15)** | `earth.worldwind.*` yerine `com.tcsappdev.worldwinddraw.*` kullanılıyor — gerçek WWK kütüphanesinin namespace'iyle karışmasın diye. |

---

## 9. Riskler ve Azaltım

| Risk | Etki | Azaltım |
|---|---|---|
| WWK'nin 2.0.5 sonrası API kırılımları | Adapter modülünde derleme hataları | Kırılım tüm etkisiyle sadece `wwk-adapter`'da kalır, `core` etkilenmez |
| ~~iOS'ta WWK yokluğu~~ (Faz 0'da geçersiz olduğu doğrulandı — WWK 2.0.5 iOS'u destekliyor) | — | `wwk-adapter` iOS'ta (`iosArm64`, `iosSimulatorArm64`) WWK ile birlikte derlenip çalışabiliyor; ayrıca gerçek cihazda/simülatörde doğrulama Faz 10'da yapılacak. |
| Composite build ile mavenLocal arası geçişte sürtünme | Geliştirme akışında kesinti | Modül yapısı (core/adapter ayrımı) her iki yönteme de bağımsız çalışır, geçiş sadece `settings.gradle.kts` değişikliği |
| Command'ların delta yerine tam kopya tutması riski (performans) | Büyük poligonlarda bellek şişmesi | Faz 3'te her komut için "sadece değişen alan" prensibi code review'da zorunlu tutulur |

---

## 10. Sonraki Adım

Faz 0 tamamlandı ve doğrulandı (`./gradlew build` yeşil). Sıradaki somut adım **Faz 1** — core domain modelleri (`GeoPosition`, `Vertex`, `DrawableEntity`, `ShapeStyle`/`StyleState`/`Theme`, `GeometryOps`).
