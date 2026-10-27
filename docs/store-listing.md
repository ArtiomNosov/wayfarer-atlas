# Карточка Google Play — черновик

Статус: тексты и восемь настоящих кадров для версии 0.1.1 (2); [техническая проверка](verification-0.1.1.md) выполнена. Перед загрузкой сверить материалы с передаваемым release AAB. Рабочие названия не подтверждают доступность бренда/пакета в Google Play. Публикацию блокируют незаполненные поля издателя/поддержки/политики и шаги владельца в Play Console. Результаты 0.1.0 остаются историей; в магазин загружаются материалы соответствующей версии.

## Русский — ru-RU

**Название (до 30 символов):** Атлас маршрутов

**Краткое описание (до 80 символов):** Карта въездных правил, сравнение стран, поездки и напоминания.

**Полное описание:**

Планируйте поездки и изучайте условия въезда с «Атласом маршрутов».

Выберите страну гражданства, чтобы посмотреть справочную карту доступности направлений. Сравнивайте доступность направлений для двух гражданств, изучайте рейтинг и открывайте источники правил. Сохраняйте поездки и включайте локальное напоминание на день их начала.

В приложении:
• карта условий въезда и справочник стран;
• сравнение доступности поездок для двух гражданств и рейтинг с пояснением методики;
• журнал поездок и напоминания;
• открытая база сообщества и отдельные официальные источники с указанием происхождения;
• обновление базы сообщества и официальных сведений Гонконга;
• русский и английский языки.

При первом запуске нужно ознакомиться с предупреждением о справочных данных; оно также доступно в настройках. Для работы не нужны регистрация, загрузка документов или номер паспорта. Поездки и настройки хранятся на устройстве и исключены из резервного копирования Android. Напоминание планируется на 09:00 в день начала поездки; Android может показать его позже. Рекламы, покупок и подписок нет. Загрузка обновлений справочника и открытие внешних источников используют интернет; принимающие серверы могут обрабатывать технические сведения о соединении согласно своим правилам.

Условия въезда зависят от гражданства, цели и длительности поездки, пересадок и индивидуальных обстоятельств. Используются открытый справочник сообщества VisaChecker.org и официальные сведения по отдельным направлениям Гонконга, Новой Зеландии и Макао. Наличие официального источника для отдельного направления не делает всю базу официальной или полностью актуальной. Не все правила имеют индивидуальную дату проверки. Данные могут меняться и не гарантируют допуск на рейс, выдачу визы или въезд. Перед поездкой проверьте актуальные требования у официальных органов и перевозчика. Приложение не связано с государственными органами и не выдаёт разрешения на въезд.

## English — en-US

**App name (up to 30 characters):** Wayfarer Atlas

**Short description (up to 80 characters):** Entry rules, country comparisons, trips and local reminders.

**Full description:**

Plan trips and explore entry requirements with Wayfarer Atlas.

Select a citizenship country to view a reference map of destinations. Compare destination access for two citizenships, explore rankings and open the underlying sources. Keep a trip journal and enable a local reminder for the start of each trip.

Features:
• entry requirements map and country reference;
• travel access comparisons for two citizenships and a ranking with its methodology;
• trip journal and local reminders;
• an open community reference and selected official sources with attribution;
• updates to the community reference and official Hong Kong information;
• English and Russian interfaces.

On first launch, acknowledge the reference-data warning; you can reopen it in Settings. No sign-up, document uploads or passport numbers are required. Your trips and preferences stay on your device and are excluded from Android backup. A reminder is scheduled for 09:00 on the trip start date; Android may deliver it later. There are no ads, purchases or subscriptions. Reference downloads and external source links use the internet; receiving servers may process technical connection information under their own policies.

Entry requirements depend on citizenship, travel purpose, length of stay, transit and personal circumstances. The app combines the public VisaChecker.org community reference with selected official sources for Hong Kong, New Zealand and Macao. An official source for one route does not make the entire dataset official or fully current. Not all rules have an individual verification date. Information can change and does not guarantee boarding, a visa or admission. Check current requirements with official authorities and your carrier before travelling. This app is independent of government authorities and does not issue entry permits.

## Источники и ограничения версии 0.1.1

Объединение четырёх источников содержит 38 822 уникальные пары, 213 гражданств и 199 направлений источников. Community-основа — 38 612 пар для 197 стран гражданства, версия 2026-08-11 и дата среза 2026-08-10. В этой основе только 38 индивидуальных дат проверки; эти числа не описывают новые официальные наложения. Карта содержит 175 контуров Natural Earth и не показывает все малые территории.

Дополнительно включены отдельные официальные источники. Их объёмы не складываются с базой напрямую: источники частично описывают одни и те же пары. Фактическое объединение проверяется отдельно:

- **Гонконг:** официальный CSV Immigration Department, 207 сопоставленных гражданств, 203 известных условия; неоднозначные случаи остаются UNKNOWN. CSV помечен «As at 31 December 2025», метаданные обновлены 11.06.2026, справочная HTML-страница пересмотрена 22.09.2026. Проверка источника 26.10.2026 не является новой датой публикации. Общий CSV обновляется по кнопке; DATA.GOV.HK Terms of Use позволяют коммерческое и некоммерческое использование с атрибуцией.
- **Новая Зеландия:** официальный срез 60 перечисленных визовых исключений с обязательным NZeTA; CC BY 3.0 NZ, проверка 26.10.2026. Обновляется с версиями приложения. Для отсутствующих в списке гражданств условие не выводится догадкой; календарные месяцы не превращаются в фиксированные дни.
- **Макао:** одна самостоятельно изложенная проверенная сводка обычной поездки Россия — Макао, без визы до 30 дней. Проверка 26.10.2026; обновляется с версиями приложения. Открытая лицензия на весь текст портала не заявляется, его таблицы и формулировки не копируются.

Справочник показывает происхождение сведений. Тип документа, цель поездки, резидентство, предварительная регистрация и персональные ограничения могут изменить условие; приложение не хранит документы и не устанавливает право конкретного человека на въезд. Первое предупреждение нельзя считать подтверждением актуальности всех строк. Лицензионные notices и источники включаются в приложение и исходники.

Онлайн-обновление 0.1.1 использует два общих файла без аккаунта/API-ключа и персонализированных параметров: raw.githubusercontent.com и www.immd.gov.hk. Обе загрузки и сохранение кэшей после холодного запуска подтверждены на подписанной 0.1.1. NZ/MO не объявляются обновлёнными этой кнопкой. Фактические Android-проверки и их границы указаны в [отчёте](verification-0.1.1.md).

## Поля, которые заполняет издатель

| Поле | Значение |
|---|---|
| Издатель / Developer | **НЕ ЗАПОЛНЕНО — требуется реальное имя/наименование** |
| Email поддержки / Support | **НЕ ЗАПОЛНЕНО — нужен рабочий публичный адрес** |
| URL политики / Privacy policy | **НЕ ЗАПОЛНЕНО — публичный HTTPS URL окончательной политики** |
| Страна регистрации издателя | **НЕ ЗАПОЛНЕНО** |
| Страны распространения | **НЕ ЗАПОЛНЕНО — выбираются отдельно** |
| Категория | Кандидат: Travel & Local; подтвердить в Console |
| Основной язык | Согласовать; подготовлены ru-RU и en-US |
| Цена | Бесплатно |
| Содержит рекламу | Нет; release-зависимости проверены |
| Доступ | Все функции без аккаунта |

## Реальные изображения

Каталог [настоящих кадров](release/store-assets/README.md) содержит восемь снимков MainActivity **0.1.1 (2)**: карта, сравнение, рейтинг и пустой журнал на RU/EN. Сняты 26.10.2026 на API 36, каждый 1080×1920, 24-bit RGB PNG; RGB-пиксели сохранены при экспорте, SHA-256 сверены с [manifest](release/store-assets/screenshots/manifest.json). Версия и происхождение указаны в [capture-metadata.json](release/store-assets/screenshots/capture-metadata.json). Схемы инструкции остаются отдельными иллюстрациями.

Google требует иконку 512×512, feature graphic 1024×500 и минимум два настоящих скриншота. [Требования Google к графике](https://support.google.com/googleplay/android-developer/answer/9866151?hl=ru).

## Release notes — версия 0.1.1

**ru-RU:** Добавлены отдельные официальные источники Гонконга, Новой Зеландии и Макао, обновление официальных сведений Гонконга и предупреждение первого запуска. Источник и ограничения сведений видны пользователю.

**en-US:** Added selected official Hong Kong, New Zealand and Macao sources, official Hong Kong updates and a first-launch reference-data warning. Source attribution and limitations remain visible.
