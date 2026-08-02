# Market App

Веб-приложение «Витрина интернет-магазина» — каталог товаров с корзиной и оформлением заказов.

## Стек технологий

- **Java 21**
- **Spring Boot 3.3.4**
- **Spring WebFlux** — реактивный веб-слой, Thymeleaf-шаблоны
- **Spring Data R2DBC** — реактивный доступ к данным
- **Spring Data Redis Reactive** — кеширование товаров
- **PostgreSQL** — база данных
- **Redis** — кеш
- **Flyway** — миграции базы данных
- **OpenAPI 3.0** — контракт payment-service, кодогенерация
- **Gradle** (Kotlin DSL) — мультимодульная сборка
- **Docker / Docker Compose** — контейнеризация (4 сервиса)
- **JUnit 5, Mockito, Testcontainers** — тестирование

## Запуск локально

### Требования

- Java 21+
- PostgreSQL (запущенный локально)
- Gradle (или использовать `./gradlew`)
- Redis

### 1. Настройка базы данных

Создайте базу данных и пользователя в PostgreSQL:

```sql
CREATE DATABASE market_app;
```

### 2. Настройка подключения

Отредактируйте `market-app/src/main/resources/application.properties`:
Отредактируйте `payment-service/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/market_app
spring.datasource.username=postgres
spring.datasource.password=ваш_пароль
```

### 3. Сборка и запуск

```bash
./gradlew bootRun
```

Приложение будет доступно по адресу: [http://localhost:8081](http://localhost:8081)

При первом запуске схема БД создаётся автоматически (Hibernate DDL), тестовые товары загружаются при старте приложения c помощью Flyway .

## Сборка Executable JAR

```bash
./gradlew bootJar
```

Запуск собранного JAR:

```bash
java -jar build/libs/market-app-0.0.1-SNAPSHOT.jar
```

## Запуск через Docker

### Требования

- Docker
- Docker Compose

### Запуск

```bash
docker compose up --build
```

Приложение и PostgreSQL поднимутся автоматически. Приложение будет доступно по адресу: [http://localhost:8081](http://localhost:8081)

### Остановка

```bash
docker compose down
```

Для удаления данных (volume):

```bash
docker compose down -v
```

## Запуск тестов

```bash
./gradlew test
```

Тесты используют Testcontainers — Docker должен быть запущен.
