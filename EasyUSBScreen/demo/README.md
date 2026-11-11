# Desenvolvimento do EasyUSBScreen

Consulte o [README principal](../README.md) para funcionalidades, instalação no Linux e créditos.

A versão atual é destinada a Linux x86-64. Use **JDK 21** e execute os comandos abaixo nesta pasta.

## Executar

```bash
./gradlew :desktopApp:run
```

## Gerar o pacote portátil Linux

```bash
./gradlew :desktopApp:packagePortableLinux
```

Saída: `desktopApp/build/distributions/EasyUSBScreen-1.0.0-linux-x86_64.tar.gz`.

## Testes da interface compartilhada

```bash
./gradlew :shared:jvmTest
```

## Estrutura

- `desktopApp/`: integração com Linux, instalação do Python/Turing, gerenciamento de processos e serviço systemd.
- `shared/`: interface e componentes Compose.
- `../themes/`: pastas completas dos temas incluídos no pacote.
