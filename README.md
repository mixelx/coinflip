## Micronaut 4.10.6 Documentation

- [User Guide](https://docs.micronaut.io/4.10.6/guide/index.html)
- [API Reference](https://docs.micronaut.io/4.10.6/api/index.html)
- [Configuration Reference](https://docs.micronaut.io/4.10.6/guide/configurationreference.html)
- [Micronaut Guides](https://guides.micronaut.io/index.html)

---

- [Micronaut Gradle Plugin documentation](https://micronaut-projects.github.io/micronaut-gradle-plugin/latest/)
- [GraalVM Gradle Plugin documentation](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html)
- [Shadow Gradle Plugin](https://gradleup.com/shadow/)

## Local Run

### Запуск PostgreSQL

```bash
docker compose up -d
```

### Проверка статуса контейнера

```bash
docker ps
```

### Подключение к БД через psql (опционально)

```bash
docker exec -it coinflip-postgres psql -U coinflip -d coinflip
```

### Запуск приложения

```bash
./gradlew run
```

### Сборка и тестирование

```bash
./gradlew test
./gradlew build
```

---

## Feature micronaut-aot documentation

- [Micronaut AOT documentation](https://micronaut-projects.github.io/micronaut-aot/latest/guide/)

## Feature http-client documentation

- [Micronaut HTTP Client documentation](https://docs.micronaut.io/latest/guide/index.html#nettyHttpClient)

## Feature serialization-jackson documentation

- [Micronaut Serialization Jackson Core documentation](https://micronaut-projects.github.io/micronaut-serialization/latest/guide/)

## Feature data-jdbc documentation

- [Micronaut Data JDBC documentation](https://micronaut-projects.github.io/micronaut-data/latest/guide/#jdbc)

## Feature flyway documentation

- [Micronaut Flyway documentation](https://micronaut-projects.github.io/micronaut-flyway/latest/guide/)
