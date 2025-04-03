# Pine Script Plugin for JetBrains IDEs

This plugin provides Pine Script language support for JetBrains IDEs (IntelliJ IDEA, WebStorm, PyCharm, etc.).

## Features

- Syntax highlighting for Pine Script files (`.pine`, `.ps`, `.pinescript`)
- Code completion for built-in functions, variables, and methods
- Parameter information for function calls
- Color preview in editor
- Documentation on hover
- New Pine Script file templates (Indicator, Strategy, Library)

## Installation

### From JetBrains Marketplace

1. Open your JetBrains IDE
2. Go to Settings/Preferences → Plugins
3. Click on "Browse repositories..."
4. Search for "Pine Script"
5. Install the plugin
6. Restart your IDE

### Manual Installation

1. Download the plugin from [Releases](https://github.com/yourusername/pine-script-intellij-plugin/releases)
2. Open your JetBrains IDE
3. Go to Settings/Preferences → Plugins
4. Click on the gear icon
5. Select "Install Plugin from Disk..."
6. Choose the downloaded ZIP file
7. Restart your IDE

## Usage

### Creating New Pine Script Files

1. Right-click in the Project window
2. Select New → Pine Script File
3. Choose the type of file (Indicator, Strategy, Library, or Empty)
4. Enter the name of the file
5. Click OK

### Features

- **Syntax Highlighting**: Pine Script keywords, functions, and variables are highlighted automatically.
- **Code Completion**: Press Ctrl+Space to see completion suggestions as you type.
- **Parameter Info**: Press Ctrl+P inside function parentheses to see parameter information.
- **Documentation**: Hover over a function or keyword to see its documentation.
- **Color Preview**: Hex colors and color.NAME values show a color preview in the editor gutter.

## Building from Source

```bash
git clone https://github.com/yourusername/pine-script-intellij-plugin.git
cd pine-script-intellij-plugin
./gradlew buildPlugin
```

The plugin will be built in `build/distributions/`.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This plugin is released under the MIT License. See the [LICENSE](LICENSE) file for details.

## Acknowledgements

This plugin was inspired by the [Pine Script VS Code Plugin](https://github.com/FFriZ/Pine-Script-v5-VS-Code). 