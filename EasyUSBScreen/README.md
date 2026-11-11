# EasyUSBScreen

Aplicativo desktop para Linux que facilita a instalação, a configuração e o gerenciamento do [Turing Smart Screen Python](https://github.com/mathoudebine/turing-smart-screen-python), com uma galeria de temas para telas USB de **480 × 320 pixels**.

A interface é desenvolvida em **Kotlin e Compose Multiplatform**. O Turing continua responsável pelo monitoramento e pela comunicação com a tela USB.

## Funcionalidades

- Instalação do Turing pela interface, com **Python Standalone próprio**, sem exigir Python previamente instalado e sem alterar o `PATH` do sistema.
- Galeria com **32 temas**: 23 temas CICLOPE do projeto e 9 temas adicionais importados do Turing, compatíveis com a orientação horizontal de 480 × 320.
- Prévia dos temas e aplicação na tela USB.
- Cópia de novos temas do pacote para a instalação gerenciada ao aplicá-los, preservando temas e fontes que já existem.
- Indicador de conexão da tela e tratamento de conflitos de uso da porta USB entre instâncias reconhecidas.
- Seleção da interface de rede para monitorar TX/RX, com identificação de Ethernet e Wi-Fi.
- Configuração do backend de sensores do próprio Turing.
- Inicialização automática do Turing antes do login, em sistemas com systemd.
- Desinstalação da cópia gerenciada pela interface.
- Pacote portátil com Java incluído, lançador com ícone e instalador de atalho para o menu do KDE.

## Plataforma e requisitos

A versão atual é destinada a **Linux x86-64**, com validação no **Kubuntu**.

Para executar o pacote portátil:

- Linux com glibc e uma sessão gráfica compatível com aplicações desktop Java/Compose.
- `tar` e `gzip` disponíveis no sistema.
- Internet para baixar o Python Standalone e instalar as dependências no primeiro uso.
- Permissão do usuário para acessar a porta serial da tela USB.
- systemd para a função de inicialização automática antes do login.

O Java necessário para executar a interface já acompanha o pacote. As leituras de sensores disponíveis dependem do hardware, do sistema e do suporte oferecido pelo Turing.

## Instalação no Linux

### 1. Extrair o pacote

Obtenha o arquivo `EasyUSBScreen-1.0.0-linux-x86_64.tar.gz` gerado pelo projeto e extraia-o em uma pasta definitiva. Por exemplo, no diretório onde o pacote foi baixado:

```bash
mkdir -p "$HOME/Programas"
tar -xzf EasyUSBScreen-1.0.0-linux-x86_64.tar.gz -C "$HOME/Programas"
cd "$HOME/Programas/EasyUSBScreen"
```

Também é possível extrair o arquivo pelo Dolphin e mover a pasta `EasyUSBScreen` para o local desejado.

### 2. Criar o atalho no menu do KDE

Dentro da pasta extraída, execute:

```bash
./instalar-atalho.sh
```

O script cria a entrada **EasyUSBScreen** no menu para o usuário atual, sem administrador. Ele também configura o ícone e os caminhos do lançador `EasyUSBScreen.desktop` na pasta do aplicativo.

Se mover a pasta posteriormente, execute `instalar-atalho.sh` novamente para atualizar o atalho. O Dolphin pode solicitar confirmação ao executar o lançador pela primeira vez.

### 3. Abrir o aplicativo

Use a entrada do menu, o lançador `EasyUSBScreen.desktop` ou execute:

```bash
./iniciar.sh
```

### 4. Preparar o Turing

No primeiro uso:

1. Clique em **Baixar e instalar** para preparar o Turing com seu Python Standalone.
2. Conecte a tela USB.
3. Escolha um tema na galeria e clique em **Aplicar tema**.
4. Em **Configurações → Rede**, selecione a interface que deseja monitorar, salve e reaplique o tema.

A instalação gerenciada fica em:

```text
~/.local/share/easyusbscreen/turing-standalone/
├── python/    # Python Standalone e dependências
└── turing/    # Turing, configuração, fontes e temas
```

## Inicialização automática

Em **Configurações → Turing**, habilite a inicialização antes do login. Essa função instala um serviço systemd do usuário e pode pedir autorização para permitir que seus serviços iniciem antes do login.

O serviço inicia o **monitor do Turing**; a janela do EasyUSBScreen não precisa permanecer aberta.

Se houver uma entrada antiga do Turing na inicialização automática do KDE, desative-a antes de habilitar essa opção, evitando duas instâncias usando a mesma tela.

O serviço gerenciado utiliza:

```text
~/.config/systemd/user/easyusbscreen-turing.service
```

A versão executada pelo IntelliJ e a versão portátil usam a mesma instalação gerenciada e o mesmo serviço para o usuário atual.

## Desinstalação

### Turing e Python Standalone

Use **Configurações → Turing → Desinstalar**. A operação encerra o monitor, remove o serviço gerenciado e apaga o Python, os temas e as configurações dessa cópia. Salve os arquivos que desejar manter antes de confirmar.

### Interface EasyUSBScreen

Remova a pasta portátil e a entrada do menu:

```text
~/.local/share/applications/br.com.infonur.easyusbscreen.desktop
```

Se `XDG_DATA_HOME` estiver configurado, a entrada estará em `$XDG_DATA_HOME/applications/`.

Remover somente a interface não desinstala o Turing nem desativa seu serviço. Para remover tudo, desinstale primeiro o Turing pela interface.

## Desenvolvimento

É necessário **JDK 21**. A partir da raiz deste repositório:

```bash
cd demo
./gradlew :desktopApp:run
```

Para gerar o pacote portátil Linux:

```bash
./gradlew :desktopApp:packagePortableLinux
```

O arquivo será criado em:

```text
demo/desktopApp/build/distributions/EasyUSBScreen-1.0.0-linux-x86_64.tar.gz
```

Estrutura principal:

```text
EasyUSBScreen/
├── demo/
│   ├── desktopApp/    # Integração com Linux, instalação e gerenciamento
│   └── shared/        # Interface Compose e componentes compartilhados
└── themes/            # Pastas completas dos temas distribuídos
```

## Créditos

### Turing Smart Screen Python

O EasyUSBScreen utiliza o [Turing Smart Screen Python](https://github.com/mathoudebine/turing-smart-screen-python), desenvolvido por **Matthieu Houdebine e colaboradores**, como motor de monitoramento, renderização e comunicação com as telas.

O Turing é distribuído sob **GNU GPL v3**, conforme sua [licença](demo/desktopApp/src/turingRuntime/turing/LICENSE). Seus avisos de autoria e licença são mantidos no código empacotado. Alguns temas CICLOPE utilizam o mecanismo de fontes de dados personalizadas do Turing.

### Temas, fontes e recursos gráficos

Os temas importados, as fontes e os recursos gráficos pertencem aos respectivos autores. Consulte os arquivos de créditos e licenças incluídos nas pastas dos recursos. Entre eles estão os [créditos do tema Cyberdeck](themes/Cyberdeck/Credits.md).

### Python e interface

- [Python Build Standalone](https://github.com/astral-sh/python-build-standalone): distribuição do Python utilizada na instalação gerenciada.
- [Kotlin](https://kotlinlang.org/) e [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/): tecnologias utilizadas na interface desktop.
