# Inventory GIF's

[English](#english) · [Русский](#русский)

## English

A client-side Minecraft mod that displays GIF and PNG animations over inventories and containers. Choose images, positions, and scales for each screen. The server does not need the mod.

This project was developed with the assistance of generative AI.

### Minecraft versions

| Minecraft | Loader | Java | Source |
| --- | --- | --- | --- |
| 26.3 | Fabric | 25 | [Fabric 26.3 branch](https://github.com/Flovwver/animated-inventory/tree/codex/fabric-26.3) |
| 26.3 | NeoForge | 25 | [NeoForge 26.3 branch](https://github.com/Flovwver/animated-inventory/tree/codex/neoforge-26.3) |
| 26.1, 26.1.1, 26.1.2 | Fabric | 25 | [Fabric 26.1.x branch](https://github.com/Flovwver/animated-inventory/tree/codex/fabric-26.1.x) |
| 26.1, 26.1.1, 26.1.2 | NeoForge | 25 | [NeoForge 26.1.x branch](https://github.com/Flovwver/animated-inventory/tree/codex/neoforge-26.1.x) |
| 1.21.1 | NeoForge | 21 | [Main branch](https://github.com/Flovwver/animated-inventory/tree/main) |

Each 26.1.x branch builds a single JAR for all three listed Minecraft versions. The 26.x branches include animated previews, per-image settings cards, and an option to discard all pending settings changes.

### Build and install

1. Switch to the branch for your Minecraft version and loader.
2. Run `./gradlew build` or `.\gradlew.bat build` on Windows.
3. Copy the JAR from `build/libs` into your client's `mods` folder.
4. Install Cloth Config for your Minecraft version and loader. Fabric also requires Fabric API. The 1.21.1 build uses NeoForge 21.1.235 and Cloth Config 15.0.140. For 26.x, see the README on the corresponding branch for dependency versions.

### Controls

- **Ctrl+O** opens settings from an inventory screen.
- **Ctrl+L** locks or unlocks images on the current screen.
- Drag an image with the left mouse button to move it. Drag its corner to resize it.
- Right-click an image to reset the screen's settings to the parent settings.

On the 26.x branches, the **Open GIF / PNG folder** button in General settings opens the current client's animation library. Place GIF or PNG files there. For a PNG sprite sheet, stack the frames vertically from top to bottom and configure them in the sprite editor.

Settings are stored in `config/inventorygif-client.toml` and `config/inventorygif-positions.json`. Images and frame settings are stored in `inventorygif/animations/` relative to the client's game directory.

### License

[MIT](LICENSE)

---

## Русский

Клиентский мод для Minecraft, который показывает GIF и PNG-анимации поверх инвентаря и контейнеров. Для каждого экрана можно настроить свои изображения, позиции и масштаб. Серверу мод не требуется.

Проект разработан с использованием генеративного ИИ.

### Версии Minecraft

| Minecraft | Загрузчик | Java | Исходники |
| --- | --- | --- | --- |
| 26.3 | Fabric | 25 | [Ветка Fabric 26.3](https://github.com/Flovwver/animated-inventory/tree/codex/fabric-26.3) |
| 26.3 | NeoForge | 25 | [Ветка NeoForge 26.3](https://github.com/Flovwver/animated-inventory/tree/codex/neoforge-26.3) |
| 26.1, 26.1.1, 26.1.2 | Fabric | 25 | [Ветка Fabric 26.1.x](https://github.com/Flovwver/animated-inventory/tree/codex/fabric-26.1.x) |
| 26.1, 26.1.1, 26.1.2 | NeoForge | 25 | [Ветка NeoForge 26.1.x](https://github.com/Flovwver/animated-inventory/tree/codex/neoforge-26.1.x) |
| 1.21.1 | NeoForge | 21 | [Основная ветка](https://github.com/Flovwver/animated-inventory/tree/main) |

Каждая ветка 26.1.x собирает один JAR для всех трёх указанных версий Minecraft. Ветки 26.x включают предпросмотр анимаций, карточки изображений и общую отмену изменений настроек.

### Сборка и установка

1. Переключитесь на ветку для своей версии Minecraft и загрузчика.
2. Запустите `./gradlew build` или `.\gradlew.bat build` в Windows.
3. Скопируйте JAR из `build/libs` в папку `mods` клиента.
4. Установите Cloth Config для своей версии игры и загрузчика. Для Fabric также нужен Fabric API. Для 1.21.1 сборка использует NeoForge 21.1.235 и Cloth Config 15.0.140. Для 26.x точные версии зависимостей указаны в README соответствующей ветки.

### Управление

- **Ctrl+O** в инвентаре открывает настройки.
- **Ctrl+L** блокирует или разблокирует изображения на текущем экране.
- Перетащите изображение левой кнопкой. Чтобы изменить размер, потяните за уголок.
- Правая кнопка по изображению сбрасывает настройки экрана к родительским.

В ветках 26.x кнопка «Открыть папку с GIF и PNG» в общих настройках открывает библиотеку текущего клиента. Положите туда GIF или PNG. Для PNG с несколькими кадрами расположите их вертикально сверху вниз и задайте параметры в редакторе кадров.

Настройки хранятся в `config/inventorygif-client.toml` и `config/inventorygif-positions.json`. Изображения и параметры кадров находятся в `inventorygif/animations/` относительно папки клиента.

### Лицензия

[MIT](LICENSE)
