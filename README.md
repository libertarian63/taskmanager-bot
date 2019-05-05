# TaskManager Bot

Бот для менеджмента задач с уведомлениями.

## Сборка проекта

Для сборки проекта используется утилита `sbt` вместе с плагином `sbt-assembly`(создаёт `fat-jar` со всеми зависимостями)

```shell
sbt assembly
```

##  Запуск 

После сборки в `target/scala-2.12` появится исполняемый файл с помощью которого можно запустить бота:

```shell
java -jar taskmanager-bot-assembly-0.1.0-SNAPSHOT.jar <bot_token> <file_for_state_store>
```