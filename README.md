# Market App

Веб-приложение «Витрина интернет-магазина» — каталог товаров с корзиной, оформлением заказов и аутентификацией покупателей.

## Стек технологий

- **Java 21**
- **Spring Boot 3.3.4**
- **Spring WebFlux** — реактивный веб-слой, Thymeleaf-шаблоны
- **Spring Security** — form login (покупатели) + OAuth2 Client (market-app → payment-service)
- **Spring Data R2DBC** — реактивный доступ к данным
- **Spring Data Redis Reactive** — кеширование товаров
- **PostgreSQL** — база данных
- **Redis** — кеш
- **Keycloak** — OAuth2 authorization server (realm `market`)
- **Flyway** — миграции базы данных
- **OpenAPI 3.0** — контракт payment-service, кодогенерация
- **Gradle** (Kotlin DSL) — мультимодульная сборка
- **Docker / Docker Compose** — контейнеризация
- **JUnit 5, Mockito, Testcontainers, WireMock** — тестирование

## Архитектура

| Контейнер | Порт (host) | Роль |
|---|---|---|
| `market-app` | 8081 | Витрина, корзина, заказы, аутентификация покупателей |
| `payment-service` | 8082 | Баланс и платежи, OAuth2 Resource Server |
| `keycloak` | 8083 | OAuth2 authorization server (realm `market`) |
| `postgres` | 5422 | Единая база данных (схема управляется Flyway из market-app) |
| `redis` | 6379 | Кеш товаров |

## Аутентификация покупателя

Покупатели аутентифицируются в `market-app` через **form login** (Spring Security, WebFlux):

- Пользователи хранятся в таблице `users` (создаётся Flyway-миграцией `V4`).
- Пароли хранятся как bcrypt-хеши (`BCryptPasswordEncoder`).
- Роли: `CUSTOMER` и `ADMIN` (в этом спринте `ADMIN` не даёт дополнительных прав — задел на будущее).
- **Саморегистрация** — на странице `/register`. Новый пользователь получает роль `CUSTOMER`.
- Тестовых аккаунтов нет — зарегистрируйтесь через форму перед проверкой сценария.
- Логаут — кнопка «Выйти» в шапке (`POST /logout`), инвалидирует сессию и cookie `SESSION`.

Анонимный посетитель видит витрину и карточку товара, но кнопки корзины/заказов скрыты (`sec:authorize` в шаблонах). Прямой POST-запрос от анонима на защищённый эндпоинт перенаправляется на `/login`.

## OAuth2 между сервисами

`market-app` обращается к `payment-service` по OAuth2 **Client Credentials Flow** через Keycloak:

- Ключ: `market-app` — клиент, `payment-service` — resource server.
- `client_id = market-app-client`, секрет задаётся переменной `CLIENT_SECRET` (см. ниже).
- Конфигурация realm хранится как код в `keycloak/realm-export.json` и импортируется при старте (`--import-realm`).
- `payment-service` проверяет `issuer` и `aud` (audience `payment-service`).

Секрет клиента **должен совпадать** в двух местах:

1. `keycloak/keycloak.env` → `CLIENT_SECRET=<секрет>` (подставляется в `realm-export.json` через `${CLIENT_SECRET}`).
2. `.env` → `OAUTH2_CLIENT_SECRET=<тот же секрет>` (передаётся в `market-app`).

> ⚠️ Это учебный проект: хранение секрета в `keycloak.env`/`.env` допустимо для локальной разработки, но не является production-практикой.

### Получить токен вручную (для отладки)

```bash
curl -X POST http://localhost:8083/realms/market/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=market-app-client&client_secret=<секрет>"
```

В ответе — `access_token`. Проверить его можно на [jwt.io](https://jwt.io) (в поле `aud` должен быть `payment-service`).

## Запуск локально

### Требования

- Java 21+
- Docker (для PostgreSQL, Redis и Keycloak)

### 1. Поднимите инфраструктуру

```bash
docker compose up -d postgres redis keycloak
```

### 2. Настройте подключение

Скопируйте `market-app/src/main/resources/application.properties.example` в `application.properties` (аналогично для `payment-service`) и заполните своими значениями (подключение к БД, OAuth2 client/issuer).

### 3. Сборка и запуск

```bash
./gradlew :market-app:bootRun
./gradlew :payment-service:bootRun
```

Приложение доступно по адресу: [http://localhost:8081](http://localhost:8081)

Схема БД создаётся Flyway-миграциями при старте `market-app` (миграции `V1`–`V5`).

## Запуск через Docker

```bash
docker compose up --build
```

Поднимутся все 5 контейнеров. Приложение: [http://localhost:8081](http://localhost:8081).

Остановка:

```bash
docker compose down          # остановить
docker compose down -v       # остановить и удалить данные (volume)
```

## Запуск тестов

```bash
./gradlew test
```

- Часть тестов (repository/integration) использует **Testcontainers** — Docker должен быть запущен.
- OAuth2-тесты (`PaymentClientConfigTest`) используют **WireMock** и не требуют реального Keycloak.
- Тесты resource server (`PaymentControllerTest`) используют `mockJwt()` — без реального Keycloak.

## Переменные окружения

| Переменная | Описание | Где задаётся |
|---|---|---|
| `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` | Учётные данные PostgreSQL | `.env` |
| `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` | Администратор Keycloak | `.env` |
| `OAUTH2_CLIENT_SECRET` | Секрет клиента `market-app-client` (для market-app) | `.env` |
| `CLIENT_SECRET` | Тот же секрет (для импорта realm в Keycloak) | `keycloak/keycloak.env` |

Шаблон всех переменных — в `.env.example`.
