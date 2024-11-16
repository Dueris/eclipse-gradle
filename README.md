# Eclipse Gradle Plugin

The Eclipse Gradle Plugin includes the necessary libraries and extensions for the [Eclipse](https://github.com/Dueris/Eclipse) minecraft plugin, including access-widners in the development environment.

## Features

- **Access Widener Integration**: Automatically applies access wideners to your development environment.
- **SpongePowered Mixin Support**: Adds Mixin libraries automagically.

## Setup

1. Add the plugin to your `build.gradle` file:
   ```gradle
   plugins {
       id("io.github.dueris.eclipse.gradle") version "1.2.0"
   }
   ```

2. Configure the plugin in your `build.gradle` file:
   ```gradle
   eclipse {
       minecraft = MinecraftVersion.MC1_21_1
       wideners = files("example.accesswidener", "anotherExample.accesswidener")
   }
   ```

## Configuration Options

### `minecraft`
Specifies the Minecraft version. For example:
```gradle
minecraft = MinecraftVersion.MC1_21_1
```

### `wideners`
Defines the access wideners to be applied. Use `files()` to specify the paths to your widener files. All widener files must be located within any project's `src/main/resources` directory.

Example:
```gradle
wideners = files("eclipse.accesswidener", "fabricapi.accesswidener")
```

## Requirements

- Java 21 or higher
- Gradle 8.6 or higher
- Access widener files in `src/main/resources`

## License
This project is licensed under GPL-3.0 with an additional permission clause:
Redistribution and use in binary form are allowed, provided that explicit permission is obtained from the author for direct integration into third-party projects.

See the [LICENSE](https://github.com/Dueris/Eclipse/blob/master/LICENSE) file for more details.

---
