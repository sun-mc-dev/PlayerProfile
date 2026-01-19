# PlayerProfile - Usage Examples & Integration Guide

## 📚 Table of Contents

1. [Basic Usage Examples](#basic-usage-examples)
2. [Permission Setup](#permission-setup)
3. [API Integration](#api-integration)
4. [Event Handling](#event-handling)
5. [LuckPerms Integration](#luckperms-integration)
6. [Advanced Scenarios](#advanced-scenarios)

---

## Basic Usage Examples

### Scenario 1: Server Owner with Multiple Roles

```bash
# As a server owner, create different profiles for different roles
/profile create owner      # Full admin access
/profile create player     # Normal player experience
/profile create builder    # Building/creative mode

# Switch between them
/profile switch owner      # Now you have all admin perms
/profile switch player     # Back to regular gameplay
```

**Permissions needed:**

```yaml
permissions:
  - profiles.use
  - profiles.create.owner
  - profiles.create.player
  - profiles.create.builder
  - profiles.max.unlimited
```

---

### Scenario 2: Staff Member with Mod Profile

```bash
# Staff member who plays normally but needs mod tools sometimes
/profile list              # Shows: default
/profile create mod        # Create mod profile
/profile switch mod        # Switch to moderation mode
# Inventory cleared, mod tools added via other plugins
/profile switch default    # Back to playing
```

**Permissions needed:**

```yaml
permissions:
  - profiles.use
  - profiles.create.mod
  - profiles.max.3
```

---

### Scenario 3: Testing New Features

```bash
# Create a test profile to try dangerous commands
/profile create test
/profile switch test
# Test whatever you want without risking main profile
/profile switch default
/profile delete test       # Clean up when done
```

---

## Permission Setup

### LuckPerms Permission Examples

```bash
# Give all players basic access with 1 profile
lp group default permission set profiles.use true
lp group default permission set profiles.max.1 true

# Allow VIP players to have 3 profiles
lp group vip permission set profiles.max.3 true
lp group vip permission set profiles.create.vip true

# Staff can create mod profile
lp group mod permission set profiles.create.mod true
lp group mod permission set profiles.max.5 true

# Admins get everything
lp group admin permission set profiles.admin true
lp group admin permission set profiles.max.unlimited true
lp group admin permission set profiles.bypass.* true
```

### Per-User Profile Permissions

```bash
# Allow specific user to create "owner" profile
lp user PlayerName permission set profiles.create.owner true

# Give user unlimited profiles
lp user PlayerName permission set profiles.max.unlimited true
```

---

## API Integration

### Example Plugin Integration

```java
package com.example.myplugin;

import me.sunmc.api.ProfileAPI;
import me.sunmc.api.event.ProfileSwitchEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class MyPlugin extends JavaPlugin implements Listener {

    private ProfileAPI profileAPI;

    @Override
    public void onEnable() {
        // Get the API
        this.profileAPI = ProfileAPI.getInstance();

        // Register events
        getServer().getPluginManager().registerEvents(this, this);
    }

    // Example: Give items based on profile
    @EventHandler
    public void onProfileSwitch(ProfileSwitchEvent event) {
        if (!event.isPost()) return; // Only handle after switch

        Player player = event.getPlayer();
        String profile = event.getToProfile();

        switch (profile) {
            case "admin":
                giveAdminTools(player);
                break;
            case "builder":
                giveBuilderTools(player);
                break;
            case "mod":
                giveModTools(player);
                break;
        }
    }

    private void giveAdminTools(Player player) {
        // Give admin tools
        player.getInventory().addItem(new ItemStack(Material.COMMAND_BLOCK));
        player.sendMessage("§aAdmin tools given!");
    }

    private void giveBuilderTools(Player player) {
        // Give WorldEdit wand, etc.
        player.getInventory().addItem(new ItemStack(Material.WOODEN_AXE));
        player.sendMessage("§aBuilder tools given!");
    }

    private void giveModTools(Player player) {
        // Give moderation compass, etc.
        player.getInventory().addItem(new ItemStack(Material.COMPASS));
        player.sendMessage("§aMod tools given!");
    }
}
```

### Creating Profiles Programmatically

```java
// Create a profile for a player
profileAPI.createProfile(player, "custom").thenAccept(success -> {
    if(success){
        player.sendMessage("§aCustom profile created!");
        // Automatically switch to it
        profileAPI.switchProfile(player, "custom");
    } else{
        player.sendMessage("§cFailed to create profile!");
    }
});
```

### Checking Profile State

```java
// Check what profile a player is using
String activeProfile = profileAPI.getActiveProfile(player.getUniqueId());
player.sendMessage("§7You are using: §e"+activeProfile);

// Check if they have a specific profile
if(profileAPI.profileExists(player.getUniqueId(), "admin")){
        player.sendMessage("§aYou have an admin profile!");
}

// List all profiles
List<String> profiles = profileAPI.getProfiles(player.getUniqueId());
player.sendMessage("§7Your profiles: §e"+String.join(", ", profiles));
```

---

## Event Handling

### Cancelling Profile Creation

```java

@EventHandler
public void onProfileCreate(ProfileCreateEvent event) {
    String profileName = event.getProfileName();

    // Don't allow profiles named "god"
    if (profileName.equalsIgnoreCase("god")) {
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cYou cannot create a profile named 'god'!");
    }

    // Require certain rank to create "vip" profile
    if (profileName.equals("vip") && !event.getPlayer().hasPermission("rank.vip")) {
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cYou need VIP rank to create a VIP profile!");
    }
}
```

### Logging Profile Switches

```java

@EventHandler
public void onProfileSwitch(ProfileSwitchEvent event) {
    if (event.isPost()) {
        // Log to console
        String msg = String.format("%s switched from '%s' to '%s'",
                event.getPlayer().getName(),
                event.getFromProfile(),
                event.getToProfile());
        getLogger().info(msg);

        // Log to database
        logToDatabase(event.getPlayer().getUniqueId(),
                event.getFromProfile(),
                event.getToProfile());
    }
}
```

### Preventing Deletion of Important Profiles

```java

@EventHandler
public void onProfileDelete(ProfileDeleteEvent event) {
    String profileName = event.getProfileName();

    // Don't allow deleting "backup" profile
    if (profileName.equals("backup")) {
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cYou cannot delete your backup profile!");
    }
}
```

---

## LuckPerms Integration

### Automatic Permission Context Switching

```java
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;

public class LuckPermsIntegration {

    private final LuckPerms luckPerms;

    @EventHandler
    public void onProfileSwitch(ProfileSwitchEvent event) {
        if (!event.isPost()) return;

        Player player = event.getPlayer();
        String profile = event.getToProfile();

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return;

        // Clear all groups
        user.data().clear(NodeType.INHERITANCE::matches);

        // Add groups based on profile
        switch (profile) {
            case "owner":
                user.data().add(InheritanceNode.builder("owner").build());
                break;
            case "admin":
                user.data().add(InheritanceNode.builder("admin").build());
                break;
            case "mod":
                user.data().add(InheritanceNode.builder("moderator").build());
                break;
            default:
                user.data().add(InheritanceNode.builder("default").build());
        }

        // Save changes
        luckPerms.getUserManager().saveUser(user);

        player.sendMessage("§aPermissions updated for profile: " + profile);
    }
}
```

---

## Advanced Scenarios

### Economy Integration (Vault)

```java
import net.milkbowl.vault.economy.Economy;

public void handleProfilePurchase(Player player, String profileType) {
    Economy economy = getEconomy();
    double cost = getProfileCost(profileType);

    if (economy.getBalance(player) < cost) {
        player.sendMessage("§cYou need $" + cost + " to buy this profile!");
        return;
    }

    // Charge the player
    economy.withdrawPlayer(player, cost);

    // Create the profile
    profileAPI.createProfile(player, profileType).thenAccept(success -> {
        if (success) {
            player.sendMessage("§aProfile '" + profileType + "' purchased for $" + cost + "!");
        } else {
            // Refund on failure
            economy.depositPlayer(player, cost);
            player.sendMessage("§cFailed to create profile! Money refunded.");
        }
    });
}
```

### Profile Templates

```java
public void createProfileFromTemplate(Player player, String profileName, String template) {
    profileAPI.createProfile(player, profileName).thenAccept(success -> {
        if (!success) {
            player.sendMessage("§cFailed to create profile!");
            return;
        }

        // Switch to new profile
        profileAPI.switchProfile(player, profileName).thenAccept(switched -> {
            if (switched) {
                // Apply template
                applyTemplate(player, template);
                player.sendMessage("§aProfile created from template: " + template);
            }
        });
    });
}

private void applyTemplate(Player player, String template) {
    switch (template) {
        case "pvp":
            player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
            player.getInventory().setItem(1, new ItemStack(Material.BOW));
            player.getInventory().setItem(8, new ItemStack(Material.GOLDEN_APPLE, 64));
            break;
        case "miner":
            player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_PICKAXE));
            player.getInventory().setItem(1, new ItemStack(Material.TORCH, 64));
            break;
        case "builder":
            player.setGameMode(GameMode.CREATIVE);
            break;
    }
}
```

### Auto-Save Integration

```java
// Save all profiles periodically
public void startAutoSave() {
    getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
        for (Player player : getServer().getOnlinePlayers()) {
            String activeProfile = profileAPI.getActiveProfile(player.getUniqueId());
            if (activeProfile != null) {
                profileAPI.saveProfile(player, activeProfile);
            }
        }
        getLogger().info("Auto-saved all active profiles");
    }, 20L * 60 * 5, 20L * 60 * 5); // Every 5 minutes
}
```

### Combat Tag Integration with External Plugin

```java

@EventHandler
public void onCombatTag(PlayerDamageByPlayerEvent event) {
    // Tag both players in your combat system
    combatManager.tagPlayer(event.getPlayer());
    combatManager.tagPlayer(event.getDamager());

    // Also tag in ProfileAPI for profile switching restriction
    profileAPI.tagCombat(event.getPlayer().getUniqueId());
    profileAPI.tagCombat(event.getDamager().getUniqueId());
}

@EventHandler
public void onCombatEnd(CombatEndEvent event) {
    // Remove from both systems
    profileAPI.removeCombatTag(event.getPlayer().getUniqueId());
}
```

---

## Performance Tips

### 1. Batch Operations

```java
// Instead of switching profiles for many players one by one
for(Player player :players) {
        profileAPI.switchProfile(player, "event");
}

// Use CompletableFuture.allOf for parallel execution
List<CompletableFuture<Boolean>> futures = new ArrayList<>();

for(Player player :players){
        futures.add(profileAPI.switchProfile(player, "event"));
}

CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() ->{
    broadcastMessage("§aAll players switched to event profile!");
});
```

### 2. Cache Profile Checks

```java
// Cache frequently checked data
private final Map<UUID, String> cachedProfiles = new ConcurrentHashMap<>();

public String getCachedProfile(UUID uuid) {
    return cachedProfiles.computeIfAbsent(uuid,
            k -> profileAPI.getActiveProfile(k));
}
```

### 3. Async Everything

```java
// Always handle API responses asynchronously
profileAPI.createProfile(player, "test").thenAcceptAsync(success ->{
        // This runs async, don't do Bukkit API calls here
        if(success){
            //Schedule sync task for Bukkit API
            Bukkit.getScheduler().runTask(plugin, () -> {
            player.sendMessage("§aProfile created!");
            });
        }
});
```

---

## Troubleshooting Common Issues

### Issue: Players lose items when switching

**Solution**: This is intended behavior. Each profile has its own inventory. If you want to transfer items, create a
custom command:

```java

@Command("transferitems")
public void transferItems(Player player) {
    String currentProfile = profileAPI.getActiveProfile(player.getUniqueId());

    // Save current inventory to a temporary storage
    ItemStack[] items = player.getInventory().getContents();

    // Switch profiles
    profileAPI.switchProfile(player, "other").thenAccept(success -> {
        if (success) {
            // Restore items in new profile
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.getInventory().setContents(items);
                player.sendMessage("§aItems transferred!");
            });
        }
    });
}
```

### Issue: Profile switches fail silently

**Solution**: Add error handling and logging:

```java
profileAPI.switchProfile(player, "admin").thenAccept(success -> {
        if(!success){
            getLogger().warning("Profile switch failed for "+player.getName());
            player.sendMessage("§cSwitch failed! Check console for details.");
        }
});
```

### Issue: Permission changes don't apply

**Solution**: Reload permissions after profile switch:

```java

@EventHandler
public void onProfileSwitch(ProfileSwitchEvent event) {
    if (event.isPost()) {
        Player player = event.getPlayer();
        // Force permission recalculation
        player.updateCommands();
        player.recalculatePermissions();
    }
}
```

---

## Best Practices

1. **Always handle CompletableFutures properly** - Don't block the main thread
2. **Use events for side effects** - Don't modify player state directly
3. **Validate profile names** - Prevent special characters or reserved names
4. **Document your integration** - Let users know how profiles interact with your plugin
5. **Test edge cases** - What happens if player logs out during switch?
6. **Respect the API** - Don't bypass it with direct database access

---

For more examples and support, visit our Discord: https://discord.gg/maDcwPV6KB