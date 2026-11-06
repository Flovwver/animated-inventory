# Inventory GIF

Клиентский мод для Minecraft, который показывает GIF и PNG-анимации поверх инвентаря и контейнеров. Для каждого экрана можно настроить свои изображения, позиции и масштаб. Серверу мод не требуется.

## Версии Minecraft

| Minecraft | Загрузчик | Java | Исходники |
| --- | --- | --- | --- |
| 26.3 | Fabric | 25 | [Ветка Fabric 26.3](https://github.com/Flovwver/animated-inventory/tree/codex/fabric-26.3) |
| 26.3 | NeoForge | 25 | [Ветка NeoForge 26.3](https://github.com/Flovwver/animated-inventory/tree/codex/neoforge-26.3) |
| 26.1, 26.1.1, 26.1.2 | Fabric | 25 | [Ветка Fabric 26.1.x](https://github.com/Flovwver/animated-inventory/tree/codex/fabric-26.1.x) |
| 26.1, 26.1.1, 26.1.2 | NeoForge | 25 | [Ветка NeoForge 26.1.x](https://github.com/Flovwver/animated-inventory/tree/codex/neoforge-26.1.x) |
| 1.21.1 | NeoForge | 21 | [Основная ветка](https://github.com/Flovwver/animated-inventory/tree/main) |

Каждая ветка 26.1.x собирает один JAR для всех трёх указанных версий Minecraft. Ветки 26.x включают предпросмотр анимаций, карточки изображений и общую отмену изменений настроек.

## Сборка и установка

1. Переключитесь на ветку для своей версии Minecraft и загрузчика.
2. Запустите `./gradlew build` или `.\gradlew.bat build` в Windows.
3. Скопируйте JAR из `build/libs` в папку `mods` клиента.
4. Установите Cloth Config для своей версии игры и загрузчика. Для Fabric также нужен Fabric API. Для 1.21.1 сборка использует NeoForge 21.1.235 и Cloth Config 15.0.140. Для 26.x точные версии зависимостей указаны в README соответствующей ветки.

## Управление

- **Ctrl+O** в инвентаре открывает настройки.
- **Ctrl+L** блокирует или разблокирует изображения на текущем экране.
- Перетащите изображение левой кнопкой. Чтобы изменить размер, потяните за уголок.
- Правая кнопка по изображению сбрасывает настройки экрана к родительским.

В ветках 26.x кнопка «Открыть папку с GIF и PNG» в общих настройках открывает библиотеку текущего клиента. Положите туда GIF или PNG. Для PNG с несколькими кадрами расположите их вертикально сверху вниз и задайте параметры в редакторе кадров.

Настройки хранятся в `config/inventorygif-client.toml` и `config/inventorygif-positions.json`. Изображения и параметры кадров находятся в `inventorygif/animations/` относительно папки клиента.

## Лицензия

[MIT](LICENSE)
