# Inventory GIF — анимация поверх ванильного инвентаря

Клиентский мод для **Minecraft 1.21.1 + NeoForge**. При открытии стандартного
инвентаря игрока (`InventoryScreen`) рядом с окном инвентаря отображается
зацикленная анимация из PNG sprite sheet (8 кадров 128×128, расположены
вертикально, итоговая текстура 128×1024).

Ванильный экран не заменяется: мод подписывается на клиентское событие
`ContainerScreenEvent.Render.Foreground` и дорисовывает анимацию поверх уже
отрисованного GUI, но под тултипами и перетаскиваемым предметом.

**Sprite sheet загружается из папки инстанса** (директории игры), а не из jar —
свой PNG можно подложить без пересборки мода. **Все параметры настраиваются
в `config/inventorygif-client.toml`**, как в большинстве NeoForge-модов.

## Структура проекта

```text
src/main/java/
    ru/example/inventorygif/
        InventoryGif.java                      # главный класс мода (@Mod), регистрация конфига
        client/
            InventoryGifRenderer.java          # клиентский рендерер (@EventBusSubscriber)
            InventoryGifConfig.java            # ModConfigSpec — все настройки
            AnimationTextureManager.java       # загрузка PNG из папки инстанса

src/main/resources/
    META-INF/
        neoforge.mods.toml                     # метаданные мода
    assets/
        inventorygif/
            textures/
                gui/
                    animation.png              # встроенный дефолт (фолбэк + источник авто-распаковки)

tools/GenerateAnimation.java                   # генератор тестовой текстуры (не часть мода)
```

## Как собрать jar

```powershell
.\gradlew.bat build
```

Готовый файл: `build\libs\inventorygif-1.0.0.jar`.
Положите его в `.minecraft\mods\` клиента с установленным **NeoForge 21.1.x**
для Minecraft 1.21.1. Сервер модифицировать не нужно.

## Как запустить dev-клиент

```powershell
.\gradlew.bat runClient
```

В игре нажмите `E` — справа от окна инвентаря появится анимация.

## Где находится анимация (PNG)

**Рабочая текстура**: `<папка инстанса>\assets\inventorygif\animation.png`
(для обычного лаунчера — `.minecraft\assets\inventorygif\animation.png`).

При первом запуске, если файла нет, мод **сам распакует туда встроенный дефолт** —
вы сразу увидите, куда класть свою картинку. Файл перечитывается автоматически
при изменении на диске (проверка раз в 0,5 с, только пока открыт инвентарь).
Если файл удалить или он битый — используется встроенная текстура из jar.

Путь можно изменить в конфиге (`texturePath`, относительно папки игры или абсолютный).

## Настройки: config/inventorygif-client.toml

Файл создаётся автоматически при первом запуске клиента. Все параметры:

| Опция | По умолчанию | Описание |
|---|---|---|
| `xOffset` | `178` | Смещение по X от угла инвентаря (`getGuiLeft()`), в GUI-пикселях. Инвентарь шириной 176 px, значит 178 — правее него |
| `yOffset` | `20` | Смещение по Y от угла инвентаря (`getGuiTop()`) |
| `scale` | `0.75` | Масштаб: `1.0` = кадр в нативном размере 128×128 px, `0.75` = 96×96 px на экране |
| `frameDurationMs` | `100` | Длительность кадра в миллисекундах (100 мс = 10 fps). Меньше — быстрее |
| `frameCount` | `8` | Число кадров в sprite sheet (кадры идут вертикально) |
| `frameSize` | `128` | Размер одного кадра в пикселях (кадры квадратные) |
| `texturePath` | `assets/inventorygif/animation.png` | Путь к PNG относительно папки игры (или абсолютный) |
| `screenMode` | `player` | Где рисовать: `player` — инвентарь выживания и творческий (+ наследники), `all` — все контейнерные экраны (сундуки, механизмы, модовые GUI вроде аксессуаров) |
| `debugDrawPlaceholder` | `false` | `true` — рисовать полупрозрачный квадрат вместо текстуры (отладка координат) |

Координаты всегда считаются от угла инвентарного GUI
(`screen.getGuiLeft() + xOffset`, `screen.getGuiTop() + yOffset`).
Если анимация не помещается по правому краю экрана, она автоматически
переносится в свободное место слева от инвентаря.


## Как заменить PNG

1. Возьмите свой sprite sheet: кадры одного размера, расположены **вертикально**
   сверху вниз (frame 0 сверху).
2. Положите его в `<папка инстанса>\assets\inventorygif\animation.png`
   (или по своему пути из `texturePath`).
3. Если размер/число кадров отличаются — поправьте `frameCount` и `frameSize`
   в `config/inventorygif-client.toml`.
4. Пересборка не нужна: файл подхватится автоматически при следующем открытии
   инвентаря (конфиг — после перезапуска игры или перезагрузки конфига).

Чтобы сгенерировать новый тестовый sprite sheet (8 кадров 128×128) в ресурсы проекта:

```powershell
java tools\GenerateAnimation.java
```

## Отладка

Если мод собран, но анимации не видно:

1. Поставьте `debugDrawPlaceholder = true` в конфиге — вместо текстуры
   нарисуется полупрозрачный квадрат. Если квадрат виден — проблема в файле
   текстуры; если нет — проверяйте координаты/событие.
2. Проверьте лог клиента: строки `Loaded animation texture from ...`,
   `Extracted default animation texture to ...` или предупреждения
   `Failed to load animation texture ...`.
3. Проверьте, что PNG существует по пути из `texturePath` и читается.

## Технические детали (NeoForge 1.21.1)

- Событие: `net.neoforged.neoforge.client.event.ContainerScreenEvent.Render.Foreground`.
  Именно оно, а не `ScreenEvent.Render.Post`: в NeoForge 1.21.1 метод
  `AbstractContainerScreen.render()` пропатчен и реплицирует `Screen.render()`
  без вызова super, поэтому `ScreenEvent.Render.Pre/Post` для контейнерных
  экранов (включая инвентарь) **не вызываются**.
- Регистрация: `@EventBusSubscriber(modid = ..., value = Dist.CLIENT)` — шина
  определяется автоматически (атрибут `bus` в 1.21.1 deprecated и игнорируется).
- Конфиг: `ModConfigSpec` + `modContainer.registerConfig(ModConfig.Type.CLIENT, ...)`
  → `config/inventorygif-client.toml`.
- Внешняя текстура: `NativeImage.read()` + `DynamicTexture`, регистрируется в
  `TextureManager` под id `inventorygif:external_animation`; путь резолвится
  от `FMLPaths.GAMEDIR`.
- Позиция GUI: `screen.getGuiLeft()` / `screen.getGuiTop()` — публичные геттеры,
  которые NeoForge добавляет в `AbstractContainerScreen` патчем.
- Отрисовка: `GuiGraphics.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight)`.
