# Building the Pine Script JetBrains Plugin

This document describes how to build the Pine Script plugin for JetBrains IDEs.

## Prerequisites

- Java JDK 17 or newer
- Git

## Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/pine-script-intellij-plugin.git
   cd pine-script-intellij-plugin
   ```

2. Build the plugin:
   ```bash
   ./gradlew buildPlugin
   ```

   This will generate the plugin ZIP file in the `build/distributions` directory.

## Installing the Plugin from the ZIP File

1. Open your JetBrains IDE (IntelliJ IDEA, WebStorm, etc.)
2. Go to **Settings/Preferences → Plugins**
3. Click on the gear icon and select **Install Plugin from Disk...**
4. Select the ZIP file from `build/distributions`
5. Restart your IDE

## Development Workflow

### Running the Plugin in Development Mode

To run the plugin in a separate IntelliJ IDEA instance:

```bash
./gradlew runIde
```

This will start a new IntelliJ IDEA instance with the plugin installed.

### Testing

To run the tests:

```bash
./gradlew test
```

### Continuous Integration

The project is set up to use GitHub Actions for CI. The workflow will build and test the plugin on every push.

## Troubleshooting

- **Problem**: Gradle fails to build
  **Solution**: Make sure you have JDK 17 or newer installed and set as the project SDK in your IDE.

- **Problem**: The plugin doesn't show up after installation
  **Solution**: Make sure to restart your IDE after installing the plugin.

## Documentation

For more information about developing JetBrains plugins, see the [JetBrains Platform SDK Documentation](https://plugins.jetbrains.com/docs/intellij/welcome.html). 