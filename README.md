Bank Card Management System

REST API для управления банковскими картами.

---

Технологии:

- Java 21
- Spring Boot 4.0.6
- Spring Security + JWT
- PostgreSQL
- Liquibase
- Docker / Docker Compose
- Swagger UI

---

Быстрый старт:

1. Требования

- [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- [Java 21](https://aws.amazon.com/corretto/) (для локального запуска)
- [Maven](https://maven.apache.org/) (для локального запуска)

---

2. Клонировать репозиторий

```bash
https://github.com/stellarsula/bank-rest.git
cd bank-rest
```

---

3. Запуск через Docker Compose

```bash
docker-compose up --build
```

Запуск в фоне:

```bash
docker-compose up --build -d
```

Остановка:

```bash
docker-compose down
```

Остановка с удалением данных БД:

```bash
docker-compose down -v
```

---

4. Локальный запуск (без Docker)

Убедитесь что PostgreSQL запущен локально и создана база `bank_rest_db`.

```bash
export $(grep -v '^#' .env | xargs) && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

---

API документация

После запуска откройте в браузере:

| Интерфейс   | URL                                          |
|-------------|----------------------------------------------|
| Swagger UI  | http://localhost:8080/swagger-ui.html        |
| OpenAPI JSON| http://localhost:8080/api-docs               |

---

Аутентификация

Все запросы (кроме `/api/v1/auth/**`) требуют JWT токен.

Получить токен

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "Admin@123"
}
```

Ответ:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGci...",
  "tokenType": "Bearer"
}
```

В каждом запросе добавляй заголовок Bearer
