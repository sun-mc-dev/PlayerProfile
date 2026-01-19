# PlayerProfile

Advanced multi-profile management system for Minecraft Paper servers (1.21.4+).

## 🌟 Features

- **Multiple Profiles per Player**: Players can have multiple completely isolated profiles
- **Full Data Separation**: Each profile stores its own inventory, armor, XP, health, effects, and more
- **Permission-Based System**: Control who can create profiles and how many they can have
- **Combat Protection**: Prevents profile switching during combat with configurable combat tags
- **Warmup System**: Configurable warmup timers with movement and damage cancellation
- **LuckPerms Integration**: Seamless permission context switching (optional)
- **High Performance**: Async operations, multithreaded, optimized database queries
- **Public API**: Full API for developers to integrate with their plugins
- **Comprehensive Events**: Listen to profile creation, deletion, switching, and more

## 📋 Requirements

- **Minecraft Version**: 1.21.4+
- **Server Software**: Paper or its forks (Purpur, Pufferfish, etc.)
- **Java Version**: 21+
- **Optional Dependencies**:
    - LuckPerms (for permission contexts)
    - Vault (for future economy integration)

## 🚀 Installation

1. Download the latest `PlayerProfile-X.X.X.jar` from releases
2. Place it in your server's `plugins/` folder
3. Restart your server
4. Configure `plugins/PlayerProfile/config.yml` to your needs
5. Set up permissions (see below)

## 📖 Commands

| Command                  | Description                 | Permission               |
|--------------------------|-----------------------------|--------------------------|
| `/profile`               | Show help menu              | `profiles.use`           |
| `/profile list`          | List all your profiles      | `profiles.use`           |
| `/profile current`       | Show current active profile | `profiles.use`           |
| `/profile create <name>` | Create a new profile        | `profiles.create.<name>` |
| `/profile switch <name>` | Switch to a profile         | `profiles.use`           |
| `/profile delete <name>` | Delete a profile            | `profiles.use`           |

**Aliases**: `/profiles`, `/prof`

## 🔐 Permissions

### Basic Permissions

- `profiles.use` - Allows basic profile usage (default: true)
- `profiles.admin` - Full profile administration (default: op)

### Profile Creation Permissions

Control which profile names users can create:

- `profiles.create.*` - Allows creating any profile
- `profiles.create.default` - Allows creating "default" profile
- `profiles.create.owner` - Allows creating "owner" profile
- `profiles.create.admin` - Allows creating "admin" profile
- `profiles.create.mod` - Allows creating "mod" profile
- `profiles.create.builder` - Allows creating "builder" profile

**Note**: Profile creation is permission-based. If you want users to create a profile named "vip", grant them
`profiles.create.vip`.

### Profile Limits

Control how many profiles a player can have:

- `profiles.max.unlimited` - Unlimited profiles (default: op)
- `profiles.max.10` - Up to 10 profiles
- `profiles.max.5` - Up to 5 profiles
- `profiles.max.3` - Up to 3 profiles
- `profiles.max.1` - Only 1 profile (default: true)

**Note**: Higher numbers take priority. If a player has both `max.3` and `max.5`, they get 5 profiles.

### Bypass Permissions

- `profiles.bypass.combat` - Bypass combat tag restriction
- `profiles.bypass.warmup` - Bypass warmup timer (instant switch)

## ⚙️ Configuration

The `config.yml` file provides extensive customization options:

```yaml
switch:
  warmup-seconds: 5          # Warmup time before switching
  cancel-on-move: true       # Cancel if player moves
  cancel-on-damage: true     # Cancel if player takes damage
  cancel-in-combat: true     # Block switching during combat
  combat-tag-duration: 10    # Combat tag duration in seconds
```

See the default `config.yml` for all available options.

## 🎯 Use Cases

### Owner/Admin Mode

```
/profile create owner
/profile switch owner
# Now you have admin perms, creative mode, etc.
/profile switch default
# Back to normal player mode
```

### Builder Profile

```
/profile create builder
# Give the "builder" profile WorldEdit perms via LuckPerms
/profile switch builder
# Now you have building tools without affecting your main profile
```

### Testing Profile

```
/profile create test
/profile switch test
# Test features without affecting your main inventory
```

## 🔧 Developer API

### Maven Dependency (just an example - api upload is remaining)

```xml

<repository>
    <id>sunmc-repo</id>
    <url>https://repo.sunmc.me</url>
</repository>

<dependency>
<groupId>me.sunmc</groupId>
<artifactId>PlayerProfile</artifactId>
<version>1.0.0</version>
<scope>provided</scope>
</dependency>
```

### API Usage

```java
// Get the API instance
ProfileAPI api = ProfileAPI.getInstance();

// Get a player's profiles
UUID uuid = player.getUniqueId();
List<String> profiles = api.getProfiles(uuid);

// Get active profile
String active = api.getActiveProfile(uuid);

// Create a profile
api.createProfile(player, "custom").thenAccept(success -> {
    if(success){
        player.sendMessage("Profile created!");
    }
});

// Switch profiles
api.switchProfile(player, "custom").thenAccept(success -> {
    if(success){
        player.sendMessage("Switched!");
    }
});

// Check if player is in combat
if(api.isInCombat(uuid)) {
    long remaining = api.getRemainingCombatTime(uuid);
    player.sendMessage("You are in combat! "+remaining +"s remaining");
}

// Force instant switch (bypasses all restrictions)
boolean switched = api.forceSwitch(player, "admin");
```

### Events

Listen to profile events:

```java
import org.jetbrains.annotations.NotNull;

@EventHandler
public void onProfileSwitch(@NotNull ProfileSwitchEvent event) {
    Player player = event.getPlayer();
    String from = event.getFromProfile();
    String to = event.getToProfile();

    if (event.isPre()) {
        // Before switch - can be cancelled
        player.sendMessage("Switching from " + from + " to " + to);
        event.setCancelled(true); // Cancel if needed
    } else {
        // After switch - cannot be cancelled
        player.sendMessage("Successfully switched!");
    }
}

@EventHandler
public void onProfileCreate(@NotNull ProfileCreateEvent event) {
    // Can be cancelled
    if (event.getProfileName().equals("banned")) {
        event.setCancelled(true);
    }
}
```

Available events:

- `ProfileLoadEvent` - When profiles are loaded for a player
- `ProfileCreateEvent` - When a profile is created (cancellable)
- `ProfileDeleteEvent` - When a profile is deleted (cancellable)
- `ProfileSwitchEvent` - When switching profiles (pre: cancellable, post: informational)

## 🗄️ Storage

### What Gets Stored Per Profile

Each profile stores:

- **Inventory**: All 36 inventory slots
- **Armor**: Helmet, chestplate, leggings, boots
- **Off-hand**: Off-hand item
- **Ender Chest**: All 27 ender chest slots
- **Experience**: Total XP, level, and XP bar progress
- **Health & Hunger**: Health, food level, saturation
- **Potion Effects**: All active effects with durations
- **Game Mode**: Survival, creative, etc. (optional)
- **Vanish State**: Whether player is vanished (optional)
- **Metadata**: Created timestamp, last used timestamp

### Database

By default, profiles are stored in SQLite (`plugins/PlayerProfile/profiles.db`).

Future versions will support:

- MySQL
- PostgreSQL
- MongoDB

## 🔒 Security & Safety

### Combat Tag System

- Players are tagged when they damage or are damaged by other players
- Configurable tag duration (default: 10 seconds)
- Prevents profile switching during combat
- Can be bypassed with `profiles.bypass.combat` permission

### Movement Detection

- Detects block-level movement (not just head rotation)
- Cancels switch if player moves during warmup
- Can be disabled in config

### Damage Detection

- Cancels switch if player takes any damage
- Includes fall damage, fire, drowning, etc.
- Can be disabled in config

## 📊 Performance

- **Async Everything**: Profile loading, saving, switching all happen async
- **Multithreaded**: Worker thread pool sized to your CPU cores
- **Efficient Caching**: Profiles cached in memory, database only for persistence
- **Optimized Queries**: Prepared statements, batch operations
- **Zero Main Thread Lag**: All heavy operations off main thread

## 🛠️ Troubleshooting

### "Profile not found" when switching

- Ensure the profile exists (`/profile list`)
- Check for typos in the profile name
- Profile names are case-sensitive

### Cannot create profile

- Check permissions (`profiles.create.<name>`)
- Verify you haven't hit your profile limit
- Check console for errors

### Switch gets cancelled immediately

- You may be in combat
- Check if you're taking damage (fire, poison, etc.)
- Verify you're not moving

## 📝 TODO / Roadmap

- [ ] MySQL/PostgreSQL support
- [ ] Web dashboard for profile management
- [ ] Profile templates
- [ ] Profile sharing/importing
- [ ] Economy integration
- [ ] Statistics tracking
- [ ] Profile backups

## 📜 License

This plugin is proprietary commercial software. All rights reserved.
Unauthorized copying, distribution, or modification is prohibited.

## 💬 Support

For support, bug reports, or feature requests:

- Discord: https://discord.gg/maDcwPV6KB
- Email: sun.minecraftdev@gmail.com
- Issue Tracker: https://github.com/sun-mc-dev/PlayerProfile/issues

## ✨ Credits

Developed by SunMC Development Team
Using Paper API, Adventure API, and LuckPerms API

---

**Made with ❤️ for the Minecraft community**