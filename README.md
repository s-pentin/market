# Market App

Веб-приложение «Витрина интернет-магазина» — каталог товаров с корзиной и оформлением заказов.

## Стек технологий

- **Java 21**
- **Spring Boot 4**
- **Spring Web MVC** — веб-слой, Thymeleaf-шаблоны
- **Spring Data JPA + Hibernate** — доступ к данным
- **PostgreSQL** — база данных
- **Flyway** - миграции баз данных
- **Gradle** — система сборки
- **Docker / Docker Compose** — контейнеризация
- **JUnit 5, Mockito, Testcontainers** — тестирование

## Запуск локально

### Требования

- Java 21+
- PostgreSQL (запущенный локально)
- Gradle (или использовать `./gradlew`)

### 1. Настройка базы данных

Создайте базу данных и пользователя в PostgreSQL:

```sql
CREATE DATABASE market_app;
```

### 2. Настройка подключения

Отредактируйте `src/main/resources/application.properties`:

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

При первом запуске схема БД создаётся автоматически (Hibernate DDL), тестовые товары загружаются из `data.sql`.

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
