package com.mafia;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * MafiaPlugin - one Boss, one Taskmaster, a family of members, real guns and
 * a task system where failure means execution.
 */
public final class MafiaPlugin extends JavaPlugin implements Listener, TabExecutor {

    // =====================================================================
    //  constants / types
    // =====================================================================

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String P = "<dark_gray>[<dark_red>☠ <red>Mafia<dark_gray>] <gray>";

    enum Role { BOSS, TASKMASTER, MEMBER }

    enum Gun {
        //      id        display           material                 dmg   mag cd  rld  spread range pel  auto   pitch boom  color            trail
        PISTOL ("pistol", "Pistol",         Material.STICK,       6.0,  12,  6,  36, 0.010,  80, 1, false, 2.0f, 0.30f, "<gray>",        0xDDDDDD),
        TOMMY  ("tommy",  "Tommy Gun",      Material.STICK,     2.8,  50,  2,  70, 0.050,  60, 1, true,  1.7f, 0.25f, "<gold>",        0xFFC94D),
        AR     ("ar",     "Assault Rifle",  Material.STICK,  3.8,  30,  3,  55, 0.020, 100, 1, true,  1.5f, 0.35f, "<dark_green>",  0x6FD36F),
        SHOTGUN("shotgun","Pump Shotgun",   Material.STICK,      2.6,   6, 22,  60, 0.110,  28, 8, false, 0.8f, 0.80f, "<red>",         0xFF6B6B),
        SNIPER ("sniper", "Sniper Rifle",   Material.STICK,      17.0,   5, 34,  70, 0.050, 170, 1, false, 1.0f, 1.00f, "<aqua>",        0x7FE5FF),
        ROCKET ("rocket", "Rocket Launcher",Material.STICK,     16.0,   1, 40,  80, 0.000, 120, 1, false, 0.6f, 1.00f, "<dark_red>",    0xFF3B1F);

        final String id, display, color;
        final Material material;
        final double damage, spread, range;
        final int mag, cooldown, reload, pellets, trail;
        final boolean auto;
        final float pitch, boom;

        Gun(String id, String display, Material material, double damage, int mag, int cooldown, int reload,
            double spread, double range, int pellets, boolean auto, float pitch, float boom, String color, int trail) {
            this.id = id;
            this.display = display;
            this.material = material;
            this.damage = damage;
            this.mag = mag;
            this.cooldown = cooldown;
            this.reload = reload;
            this.spread = spread;
            this.range = range;
            this.pellets = pellets;
            this.auto = auto;
            this.pitch = pitch;
            this.boom = boom;
            this.color = color;
            this.trail = trail;
        }
    }

    private static List<PotionEffectType> familyEffects() {
        return List.of(PotionEffectType.RESISTANCE, PotionEffectType.STRENGTH, PotionEffectType.SPEED,
                PotionEffectType.HEALTH_BOOST, PotionEffectType.FIRE_RESISTANCE);
    }

    // =====================================================================
    //  state
    // =====================================================================

    private NamespacedKey itemKey, gunKey, hatKey, roleKey, rocketKey;
    private File dataFile;

    private UUID boss;
    private UUID taskmaster;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, String> tasks = new HashMap<>();
    private final Set<UUID> awaiting = new HashSet<>();
    private final Set<UUID> pendingExec = new HashSet<>();
    private final Set<UUID> executed = new HashSet<>();
    private final Map<UUID, Long> joinRequests = new HashMap<>();

    private final Map<UUID, Map<Gun, Integer>> ammo = new HashMap<>();
    private final Map<UUID, BukkitTask> reloads = new HashMap<>();
    private final Map<UUID, BukkitTask> bursts = new HashMap<>();
    private final Map<UUID, Integer> nextFire = new HashMap<>();
    private final Map<UUID, Integer> lastAim = new HashMap<>();
    private final Map<UUID, Integer> lastBlockUse = new HashMap<>();
    /** true while the plugin itself is applying gun damage (so the melee-cancel below ignores it) */
    private boolean inShot = false;

    private Team tBoss, tTask, tMember;

    // =====================================================================
    //  lifecycle
    // =====================================================================

    @Override
    public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "mafia_item");
        gunKey = new NamespacedKey(this, "gun");
        hatKey = new NamespacedKey(this, "mafia_hat");
        roleKey = new NamespacedKey(this, "issued_role");
        rocketKey = new NamespacedKey(this, "rocket");

        getDataFolder().mkdirs();
        dataFile = new File(getDataFolder(), "data.yml");
        load();
        initTeams();

        getServer().getPluginManager().registerEvents(this, this);
        for (String n : List.of("mafia", "taskmafia", "taskcomplete", "forcejoinmember", "forcejointaskmaster",
                "forcejoinboss", "mafiahat", "mafiachat", "mafiareset")) {
            if (getCommand(n) != null) {
                getCommand(n).setExecutor(this);
                getCommand(n).setTabCompleter(this);
            }
        }

        // keep the infinite effects alive (milk, /effect clear, etc.)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (inFamily(p.getUniqueId())) applyEffects(p);
            }
        }, 100L, 100L);

        getLogger().info("The Family is open for business.");
    }

    @Override
    public void onDisable() {
        save();
    }

    // =====================================================================
    //  persistence
    // =====================================================================

    private UUID uuid(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void load() {
        YamlConfiguration c = YamlConfiguration.loadConfiguration(dataFile);
        boss = uuid(c.getString("boss"));
        taskmaster = uuid(c.getString("taskmaster"));
        for (String s : c.getStringList("members")) {
            UUID u = uuid(s);
            if (u != null) members.add(u);
        }
        ConfigurationSection n = c.getConfigurationSection("names");
        if (n != null) for (String k : n.getKeys(false)) {
            UUID u = uuid(k);
            if (u != null) names.put(u, n.getString(k, "?"));
        }
        ConfigurationSection t = c.getConfigurationSection("tasks");
        if (t != null) for (String k : t.getKeys(false)) {
            UUID u = uuid(k);
            if (u != null) tasks.put(u, t.getString(k, ""));
        }
        for (String s : c.getStringList("awaiting")) {
            UUID u = uuid(s);
            if (u != null) awaiting.add(u);
        }
        for (String s : c.getStringList("pending-exec")) {
            UUID u = uuid(s);
            if (u != null) pendingExec.add(u);
        }
    }

    private void save() {
        YamlConfiguration c = new YamlConfiguration();
        c.set("boss", boss == null ? null : boss.toString());
        c.set("taskmaster", taskmaster == null ? null : taskmaster.toString());
        c.set("members", members.stream().map(UUID::toString).toList());
        for (Map.Entry<UUID, String> e : names.entrySet()) c.set("names." + e.getKey(), e.getValue());
        for (Map.Entry<UUID, String> e : tasks.entrySet()) c.set("tasks." + e.getKey(), e.getValue());
        c.set("awaiting", awaiting.stream().map(UUID::toString).toList());
        c.set("pending-exec", pendingExec.stream().map(UUID::toString).toList());
        try {
            c.save(dataFile);
        } catch (IOException ex) {
            getLogger().warning("Could not save data.yml: " + ex.getMessage());
        }
    }

    // =====================================================================
    //  roles
    // =====================================================================

    private Role roleOf(UUID id) {
        if (id.equals(boss)) return Role.BOSS;
        if (id.equals(taskmaster)) return Role.TASKMASTER;
        if (members.contains(id)) return Role.MEMBER;
        return null;
    }

    private boolean inFamily(UUID id) {
        return roleOf(id) != null;
    }

    private List<UUID> allFamily() {
        List<UUID> out = new ArrayList<>();
        if (boss != null) out.add(boss);
        if (taskmaster != null) out.add(taskmaster);
        out.addAll(members);
        return out;
    }

    private String nameOf(UUID id) {
        String n = names.get(id);
        if (n != null) return n;
        String o = Bukkit.getOfflinePlayer(id).getName();
        return o == null ? "unknown" : o;
    }

    private UUID findMember(String name) {
        for (UUID u : members) if (nameOf(u).equalsIgnoreCase(name)) return u;
        return null;
    }

    private UUID reviewerId() {
        return taskmaster != null ? taskmaster : boss;
    }

    private boolean isReviewer(UUID id) {
        return id.equals(boss) || id.equals(taskmaster);
    }

    /** wipes every trace of a role/task for this player (does not touch items) */
    private void clearRole(UUID id) {
        if (id.equals(boss)) boss = null;
        if (id.equals(taskmaster)) taskmaster = null;
        members.remove(id);
        tasks.remove(id);
        awaiting.remove(id);
        joinRequests.remove(id);
    }

    private void setRole(UUID id, Role r) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) names.put(id, p.getName());
        clearRole(id);
        switch (r) {
            case BOSS -> boss = id;
            case TASKMASTER -> taskmaster = id;
            case MEMBER -> members.add(id);
        }
        syncTeam(id);
        save();
        if (p != null) giveLoadout(p, true);
    }

    private void removeFromFamily(UUID id) {
        Player p = Bukkit.getPlayer(id);
        clearRole(id);
        syncTeam(id);
        names.remove(id);
        save();
        if (p != null) {
            stripItems(p);
            removeFamilyEffects(p);
        }
    }

    private void tellFamily(String mm) {
        for (UUID u : allFamily()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) send(p, mm);
        }
    }

    // =====================================================================
    //  scoreboard teams (name colours / prefixes)
    // =====================================================================

    private void initTeams() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        tBoss = team(sb, "mafia_boss", NamedTextColor.DARK_RED, "<dark_red>[Boss] ");
        tTask = team(sb, "mafia_task", NamedTextColor.GOLD, "<gold>[Taskmaster] ");
        tMember = team(sb, "mafia_member", NamedTextColor.GRAY, "<gray>[Mafia] ");
        for (Team t : List.of(tBoss, tTask, tMember)) {
            new ArrayList<>(t.getEntries()).forEach(t::removeEntry);
        }
        for (UUID u : allFamily()) syncTeam(u);
    }

    private Team team(Scoreboard sb, String name, NamedTextColor color, String prefix) {
        Team t = sb.getTeam(name);
        if (t == null) t = sb.registerNewTeam(name);
        t.color(color);
        t.prefix(MM.deserialize(prefix));
        return t;
    }

    private void syncTeam(UUID id) {
        if (tBoss == null) return;
        String n = nameOf(id);
        tBoss.removeEntry(n);
        tTask.removeEntry(n);
        tMember.removeEntry(n);
        Role r = roleOf(id);
        if (r == null) return;
        switch (r) {
            case BOSS -> tBoss.addEntry(n);
            case TASKMASTER -> tTask.addEntry(n);
            case MEMBER -> tMember.addEntry(n);
        }
    }

    // =====================================================================
    //  items
    // =====================================================================

    private Component L(String mm) {
        return MM.deserialize("<!italic>" + mm);
    }

    private void tagMeta(ItemMeta m, Role r) {
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        m.getPersistentDataContainer().set(roleKey, PersistentDataType.STRING, r.name());
    }

    private boolean isMafiaItem(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    private boolean isHat(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(hatKey, PersistentDataType.BYTE);
    }

    private String issuedRole(ItemStack it) {
        return it.getItemMeta().getPersistentDataContainer().get(roleKey, PersistentDataType.STRING);
    }

    private Gun gunOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(gunKey, PersistentDataType.STRING);
        if (id == null) return null;
        for (Gun g : Gun.values()) if (g.id.equals(id)) return g;
        return null;
    }

    private ItemStack hatItem() {
        ItemStack it = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta m = (LeatherArmorMeta) it.getItemMeta();
        m.setColor(Color.fromRGB(0x151515));
        m.displayName(L("<gradient:#555555:#aa0000><bold>Mafia Hat"));
        m.lore(List.of(
                L("<gray>Hold it and <yellow>right-click<gray> to become"),
                L("<gray>the <dark_red><bold>Mafia Boss</bold><gray> of this server."),
                Component.empty(),
                L("<dark_gray>There can only be one Don.")));
        m.setEnchantmentGlintOverride(true);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        m.getPersistentDataContainer().set(hatKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return withHat(it, "mafia_hat");
    }

    private String fmt(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    private ItemStack gunItem(Gun g, Role r) {
        ItemStack it = new ItemStack(g.material);
        ItemMeta m = it.getItemMeta();
        m.displayName(L(g.color + "<bold>" + g.display));
        String dmg = g.pellets > 1 ? fmt(g.damage / 2) + " ❤ x" + g.pellets : fmt(g.damage / 2) + " ❤";
        List<Component> lore = new ArrayList<>();
        lore.add(L("<gray>Damage: <red>" + dmg + (g == Gun.ROCKET ? " <gray>(blast)" : "")));
        lore.add(L("<gray>Magazine: <white>" + g.mag));
        lore.add(L("<gray>Reload: <white>" + fmt(g.reload / 20.0) + "s"));
        lore.add(L("<gray>Range: <white>" + (int) g.range + " blocks"));
        lore.add(Component.empty());
        int burst = burstOf(g);
        lore.add(L("<yellow>Left-click: <white>shoot" + (burst > 1 ? " <gray>(" + burst + "-round burst)" : "")));
        lore.add(L("<yellow>Hold right-click: <white>aim <gray>(steadier, zoomed in, slower)"));
        lore.add(L("<yellow>F / Q: <white>reload"));
        lore.add(Component.empty());
        lore.add(L("<dark_gray>Mafia Issue · Soulbound"));
        m.lore(lore);
        m.setUnbreakable(true);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        m.getPersistentDataContainer().set(gunKey, PersistentDataType.STRING, g.id);
        m.setItemModel(NamespacedKey.fromString("mafia:" + g.id));   // resource pack model (MafiaPack)
        tagMeta(m, r);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack armorPiece(Material mat, String name, int rgb, int prot, Role r) {
        ItemStack it = new ItemStack(mat);
        LeatherArmorMeta m = (LeatherArmorMeta) it.getItemMeta();
        m.setColor(Color.fromRGB(rgb));
        m.displayName(L(name));
        if (prot > 0) m.addEnchant(Enchantment.PROTECTION, prot, true);
        m.setUnbreakable(true);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        m.setEnchantmentGlintOverride(false);
        m.lore(List.of(L("<dark_gray>Mafia Issue · Soulbound")));
        tagMeta(m, r);
        it.setItemMeta(m);
        return it;
    }

    /**
     * Gives a helmet a 3D hat model from the resource pack. Clearing the equippable asset makes the
     * client draw the item's own model on the player's head instead of the flat leather armor layer.
     * The helmet keeps its armor points / enchants.
     */
    private ItemStack withHat(ItemStack it, String model) {
        ItemMeta m = it.getItemMeta();
        m.setItemModel(NamespacedKey.fromString("mafia:" + model));
        EquippableComponent eq = m.getEquippable();
        eq.setSlot(EquipmentSlot.HEAD);
        eq.setModel(null);
        m.setEquippable(eq);
        it.setItemMeta(m);
        return it;
    }

    private void giveOrDrop(Player p, ItemStack it) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        for (ItemStack l : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
    }

    private void wear(Player p, ItemStack helmet, ItemStack chest, ItemStack legs, ItemStack boots) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] cur = inv.getArmorContents(); // boots, legs, chest, helmet
        ItemStack[] neu = {boots, legs, chest, helmet};
        for (int i = 0; i < 4; i++) {
            if (neu[i] == null) continue;
            ItemStack old = cur[i];
            if (old != null && old.getType() != Material.AIR && !isMafiaItem(old)) giveOrDrop(p, old);
            cur[i] = neu[i];
        }
        inv.setArmorContents(cur);
    }

    /** removes every Mafia-issued item, returns true if anything was removed */
    private boolean stripItems(Player p) {
        PlayerInventory inv = p.getInventory();
        boolean any = false;
        ItemStack[] all = inv.getContents();
        for (int i = 0; i < all.length; i++) {
            if (isMafiaItem(all[i])) {
                inv.setItem(i, null);
                any = true;
            }
        }
        ammo.remove(p.getUniqueId());
        return any;
    }

    private void removeFamilyEffects(Player p) {
        for (PotionEffectType t : familyEffects()) {
            PotionEffect cur = p.getPotionEffect(t);
            if (cur != null && cur.isInfinite()) p.removePotionEffect(t);
        }
    }

    private boolean needsLoadout(Player p, Role r) {
        boolean found = false;
        for (ItemStack it : p.getInventory().getContents()) {
            if (!isMafiaItem(it)) continue;
            found = true;
            if (!r.name().equals(issuedRole(it))) return true;
        }
        return !found;
    }

    private void giveLoadout(Player p, boolean fresh) {
        Role r = roleOf(p.getUniqueId());
        if (r == null) return;
        stripItems(p);
        switch (r) {
            case BOSS -> {
                wear(p,
                        withHat(armorPiece(Material.LEATHER_HELMET, "<dark_red><bold>Don's Fedora", 0x0D0D0D, 6, r), "mafia_hat"),
                        armorPiece(Material.LEATHER_CHESTPLATE, "<dark_red><bold>Don's Pinstripe Coat", 0x1A1A1A, 6, r),
                        armorPiece(Material.LEATHER_LEGGINGS, "<dark_red><bold>Don's Trousers", 0x1A1A1A, 6, r),
                        armorPiece(Material.LEATHER_BOOTS, "<dark_red><bold>Don's Oxfords", 0x0D0D0D, 6, r));
                for (Gun g : new Gun[]{Gun.PISTOL, Gun.TOMMY, Gun.AR, Gun.SHOTGUN, Gun.SNIPER, Gun.ROCKET}) {
                    giveOrDrop(p, gunItem(g, r));
                }
                if (fresh) giveOrDrop(p, new ItemStack(Material.GOLDEN_APPLE, 8));
            }
            case TASKMASTER -> {
                wear(p,
                        withHat(armorPiece(Material.LEATHER_HELMET, "<gold><bold>Taskmaster's Bowler", 0x4A0F0F, 4, r), "taskmaster_bowler"),
                        armorPiece(Material.LEATHER_CHESTPLATE, "<gold><bold>Taskmaster's Vest", 0x4A0F0F, 4, r),
                        armorPiece(Material.LEATHER_LEGGINGS, "<gold><bold>Taskmaster's Slacks", 0x2B2B2B, 4, r),
                        armorPiece(Material.LEATHER_BOOTS, "<gold><bold>Taskmaster's Brogues", 0x2B2B2B, 4, r));
                for (Gun g : new Gun[]{Gun.PISTOL, Gun.TOMMY, Gun.SHOTGUN}) giveOrDrop(p, gunItem(g, r));
                if (fresh) giveOrDrop(p, new ItemStack(Material.GOLDEN_APPLE, 4));
            }
            case MEMBER -> {
                if (getConfig().getBoolean("member-fedora", true)) {
                    wear(p, withHat(armorPiece(Material.LEATHER_HELMET, "<gray><bold>Family Fedora", 0x2A2A2A, 0, r), "family_fedora"),
                            null, null, null);
                }
                giveOrDrop(p, gunItem(Gun.PISTOL, r));
            }
        }
        applyEffects(p);
        if (fresh) {
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) p.setHealth(max.getValue());
        }
    }

    private void applyEffects(Player p) {
        Role r = roleOf(p.getUniqueId());
        if (r == null) return;
        switch (r) {
            case BOSS -> {
                eff(p, PotionEffectType.RESISTANCE, 0);
                eff(p, PotionEffectType.STRENGTH, 1);
                eff(p, PotionEffectType.SPEED, 2);
                eff(p, PotionEffectType.HEALTH_BOOST, 4);
                eff(p, PotionEffectType.FIRE_RESISTANCE, 0);
            }
            case TASKMASTER -> {
                eff(p, PotionEffectType.SPEED, 0);
                eff(p, PotionEffectType.HEALTH_BOOST, 0);
            }
            default -> { }
        }
    }

    private void eff(Player p, PotionEffectType type, int amp) {
        PotionEffect cur = p.getPotionEffect(type);
        if (cur != null && cur.isInfinite() && cur.getAmplifier() >= amp) return;
        p.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amp, false, false, true));
    }

    // =====================================================================
    //  messaging
    // =====================================================================

    private String esc(String s) {
        return MM.escapeTags(s);
    }

    private void send(CommandSender s, String mm) {
        s.sendMessage(MM.deserialize(P + mm));
    }

    private void err(CommandSender s, String mm) {
        s.sendMessage(MM.deserialize(P + "<red>" + mm));
    }

    // =====================================================================
    //  becoming the boss
    // =====================================================================

    private void becomeBoss(Player p) {
        setRole(p.getUniqueId(), Role.BOSS);
        p.getWorld().strikeLightningEffect(p.getLocation());
        p.showTitle(Title.title(MM.deserialize("<dark_red><bold>THE DON"), MM.deserialize("<gray>This town is yours now.")));
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 0.8f);
        Bukkit.broadcast(MM.deserialize(P + "<dark_red><bold>" + p.getName() + "</bold> <gray>has put on the hat and is now the <dark_red>Mafia Boss<gray>. Use <yellow>/mafia join <gray>to ask for a place in the family."));
    }

    private void claimWithHat(Player p) {
        UUID id = p.getUniqueId();
        if (id.equals(boss)) {
            err(p, "You're already the Boss.");
            return;
        }
        if (boss != null) {
            err(p, "<white>" + nameOf(boss) + "</white> already runs the family. There can only be one Don.");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getAmount() <= 1) p.getInventory().setItemInMainHand(null);
        else hand.setAmount(hand.getAmount() - 1);
        becomeBoss(p);
    }

    // =====================================================================
    //  commands
    // =====================================================================

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        switch (c.getName().toLowerCase()) {
            case "mafia" -> mafia(s, a);
            case "taskmafia" -> taskMafia(s, a);
            case "taskcomplete" -> taskComplete(s);
            case "forcejoinmember" -> forceJoin(s, a, Role.MEMBER);
            case "forcejointaskmaster" -> forceJoin(s, a, Role.TASKMASTER);
            case "forcejoinboss" -> forceJoin(s, a, Role.BOSS);
            case "mafiahat" -> giveHat(s, a);
            case "mafiachat" -> familyChat(s, a);
            case "mafiareset" -> reset(s);
            default -> { }
        }
        return true;
    }

    private void mafia(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return;
        }
        if (a.length == 0) {
            help(p);
            return;
        }
        switch (a[0].toLowerCase()) {
            case "join" -> join(p);
            case "accept" -> decide(p, a, true);
            case "decline" -> decide(p, a, false);
            case "appoint" -> appoint(p, a);
            case "demote" -> demote(p);
            case "kick" -> kick(p, a);
            case "leave" -> leave(p);
            case "resign" -> resign(p, a);
            case "info", "list" -> info(p);
            case "loadout" -> {
                if (!inFamily(p.getUniqueId())) err(p, "You're not in the family.");
                else {
                    giveLoadout(p, false);
                    send(p, "<green>Your gear has been re-issued.");
                }
            }
            case "taskaccept" -> review(p, a, true);
            case "taskdecline" -> review(p, a, false);
            default -> help(p);
        }
    }

    private void help(Player p) {
        Role r = roleOf(p.getUniqueId());
        StringBuilder sb = new StringBuilder();
        sb.append("<dark_gray><strikethrough>          </strikethrough> <dark_red><bold>MAFIA</bold> <dark_gray><strikethrough>          </strikethrough>\n");
        sb.append("<yellow>/mafia join <gray>- ask the Boss to let you in\n");
        sb.append("<yellow>/mafia info <gray>- see the family\n");
        sb.append("<yellow>/mafiachat <msg> <gray>(/mc) - talk to the family\n");
        if (r != null) {
            sb.append("<yellow>/mafia loadout <gray>- re-issue your gear\n");
            sb.append("<yellow>/mafia leave <gray>- walk away (not for the Boss)\n");
        }
        if (r == Role.MEMBER) sb.append("<yellow>/taskcomplete <gray>- report your task as done\n");
        if (r == Role.TASKMASTER || r == Role.BOSS) {
            sb.append("<yellow>/taskmafia <member|all> <task> <gray>- hand out a task\n");
        }
        if (r == Role.BOSS) {
            sb.append("<yellow>/mafia accept|decline <player> <gray>- join requests\n");
            sb.append("<yellow>/mafia appoint <member> <gray>- name a Taskmaster\n");
            sb.append("<yellow>/mafia demote <gray>- remove the Taskmaster\n");
            sb.append("<yellow>/mafia kick <member> <gray>- throw someone out\n");
            sb.append("<yellow>/mafia resign confirm <gray>- dissolve the family\n");
        }
        p.sendMessage(MM.deserialize(sb.toString()));
    }

    // ---------------------------------------------------------------- join

    private void join(Player p) {
        UUID id = p.getUniqueId();
        if (boss == null) {
            err(p, "There's no Boss yet. Somebody has to put on the Mafia Hat first.");
            return;
        }
        if (inFamily(id)) {
            err(p, "You're already in the family.");
            return;
        }
        Player b = Bukkit.getPlayer(boss);
        if (b == null) {
            err(p, "The Boss isn't online right now. Try again later.");
            return;
        }
        Long exp = joinRequests.get(id);
        if (exp != null && exp > System.currentTimeMillis()) {
            err(p, "You already have a request waiting.");
            return;
        }
        int secs = getConfig().getInt("join-request-seconds", 120);
        joinRequests.put(id, System.currentTimeMillis() + secs * 1000L);
        send(p, "<yellow>Request sent. <gray>The Boss has <white>" + secs + "s<gray> to decide.");
        b.sendMessage(MM.deserialize("\n" + P + "<yellow>" + p.getName() + " <gray>wants to join your family.\n"
                + "  <click:run_command:'/mafia accept " + p.getName() + "'><hover:show_text:'<green>Let them in'><green><bold>[ ACCEPT ]</bold></green></hover></click>"
                + "   <click:run_command:'/mafia decline " + p.getName() + "'><hover:show_text:'<red>Turn them away'><red><bold>[ DECLINE ]</bold></red></hover></click>\n"));
        b.playSound(b.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.3f);
    }

    private void decide(Player p, String[] a, boolean accept) {
        if (!p.getUniqueId().equals(boss)) {
            err(p, "Only the Boss decides who joins.");
            return;
        }
        if (a.length < 2) {
            err(p, "Usage: /mafia " + (accept ? "accept" : "decline") + " <player>");
            return;
        }
        Player t = Bukkit.getPlayerExact(a[1]);
        Long exp = t == null ? null : joinRequests.get(t.getUniqueId());
        if (t == null || exp == null || exp < System.currentTimeMillis()) {
            err(p, "No pending request from <white>" + esc(a[1]) + "</white>.");
            return;
        }
        joinRequests.remove(t.getUniqueId());
        if (accept) {
            setRole(t.getUniqueId(), Role.MEMBER);
            send(p, "<green>" + t.getName() + " <gray>is now part of the family.");
            send(t, "<green>The Boss accepted you. <gray>Welcome to the family. Check your hotbar.");
            t.playSound(t.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
            tellFamily("<green>" + t.getName() + " <gray>joined the family.");
        } else {
            send(t, "<red>The Boss turned you away.");
            send(p, "<gray>Request declined.");
        }
    }

    // ------------------------------------------------------- appoint/demote

    private void appoint(Player p, String[] a) {
        if (!p.getUniqueId().equals(boss)) {
            err(p, "Only the Boss can appoint a Taskmaster.");
            return;
        }
        if (a.length < 2) {
            err(p, "Usage: /mafia appoint <member>");
            return;
        }
        UUID t = findMember(a[1]);
        if (t == null) {
            err(p, "<white>" + esc(a[1]) + "</white> isn't a regular member of your family.");
            return;
        }
        if (taskmaster != null) {
            UUID old = taskmaster;
            setRole(old, Role.MEMBER);
            Player op = Bukkit.getPlayer(old);
            if (op != null) send(op, "<red>The Boss has replaced you as Taskmaster.");
        }
        setRole(t, Role.TASKMASTER);
        String n = nameOf(t);
        send(p, "<gold>" + n + " <gray>is now your Taskmaster.");
        Player tp = Bukkit.getPlayer(t);
        if (tp != null) {
            tp.showTitle(Title.title(MM.deserialize("<gold><bold>TASKMASTER"), MM.deserialize("<gray>Keep the family in line.")));
            tp.playSound(tp.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.6f);
            send(tp, "<gold>The Boss made you Taskmaster. <gray>Use <yellow>/taskmafia <member> <task><gray>.");
        }
        tellFamily("<gold>" + n + " <gray>is the new Taskmaster.");
    }

    private void demote(Player p) {
        if (!p.getUniqueId().equals(boss)) {
            err(p, "Only the Boss can demote the Taskmaster.");
            return;
        }
        if (taskmaster == null) {
            err(p, "You don't have a Taskmaster.");
            return;
        }
        UUID old = taskmaster;
        setRole(old, Role.MEMBER);
        Player op = Bukkit.getPlayer(old);
        if (op != null) send(op, "<red>The Boss stripped you of your Taskmaster title.");
        tellFamily("<gray>" + nameOf(old) + " <red>is no longer Taskmaster.");
    }

    private void kick(Player p, String[] a) {
        if (!p.getUniqueId().equals(boss)) {
            err(p, "Only the Boss can throw someone out.");
            return;
        }
        if (a.length < 2) {
            err(p, "Usage: /mafia kick <member>");
            return;
        }
        UUID t = findMember(a[1]);
        if (t == null && taskmaster != null && nameOf(taskmaster).equalsIgnoreCase(a[1])) t = taskmaster;
        if (t == null) {
            err(p, "<white>" + esc(a[1]) + "</white> isn't in your family.");
            return;
        }
        String n = nameOf(t);
        Player tp = Bukkit.getPlayer(t);
        removeFromFamily(t);
        if (tp != null) send(tp, "<red>The Boss threw you out of the family.");
        tellFamily("<gray>" + n + " <red>was thrown out of the family.");
        send(p, "<gray>" + n + " is out.");
    }

    private void leave(Player p) {
        UUID id = p.getUniqueId();
        if (!inFamily(id)) {
            err(p, "You're not in the family.");
            return;
        }
        if (id.equals(boss)) {
            err(p, "The Boss doesn't walk away. Use <yellow>/mafia resign confirm<red> to dissolve the family.");
            return;
        }
        removeFromFamily(id);
        send(p, "<gray>You walked away from the family.");
        tellFamily("<gray>" + p.getName() + " <red>left the family.");
    }

    private void resign(Player p, String[] a) {
        if (!p.getUniqueId().equals(boss)) {
            err(p, "Only the Boss can dissolve the family.");
            return;
        }
        if (a.length < 2 || !a[1].equalsIgnoreCase("confirm")) {
            err(p, "This kicks <white>everyone</white> out and ends your reign. Type <yellow>/mafia resign confirm<red> to do it.");
            return;
        }
        disband();
        Bukkit.broadcast(MM.deserialize(P + "<red>The Boss has stepped down. The family is dissolved."));
    }

    private void disband() {
        for (UUID u : allFamily()) removeFromFamily(u);
        joinRequests.clear();
        tasks.clear();
        awaiting.clear();
        save();
    }

    private void info(Player p) {
        if (boss == null && taskmaster == null && members.isEmpty()) {
            send(p, "<gray>There's no family yet. A <white>Mafia Hat<gray> makes you the Boss.");
            return;
        }
        boolean auth = isReviewer(p.getUniqueId());
        p.sendMessage(MM.deserialize("<dark_gray><strikethrough>          </strikethrough> <dark_red><bold>THE FAMILY</bold> <dark_gray><strikethrough>          </strikethrough>"));
        if (boss != null) p.sendMessage(MM.deserialize("<dark_red>☠ Boss: " + status(boss)));
        if (taskmaster != null) p.sendMessage(MM.deserialize("<gold>★ Taskmaster: " + status(taskmaster)));
        p.sendMessage(MM.deserialize("<gray>⚔ Members (" + members.size() + "):"));
        for (UUID u : members) {
            String line = "  " + status(u);
            String task = tasks.get(u);
            if (auth && task != null) {
                boolean waiting = awaiting.contains(u);
                p.sendMessage(MM.deserialize(line + " <dark_gray>- " + (waiting ? "<green>✔ done, awaiting review: " : "<yellow>task: ") + "<white><task>",
                        Placeholder.unparsed("task", task)));
            } else {
                p.sendMessage(MM.deserialize(line));
            }
        }
    }

    private String status(UUID u) {
        return (Bukkit.getPlayer(u) != null ? "<green>● " : "<dark_gray>● ") + "<white>" + esc(nameOf(u));
    }

    // --------------------------------------------------------------- tasks

    private void taskMafia(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return;
        }
        if (!isReviewer(p.getUniqueId())) {
            err(p, "Only the Taskmaster hands out tasks.");
            return;
        }
        if (a.length < 2) {
            err(p, "Usage: /taskmafia <member|all> <task>");
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(a, 1, a.length)).trim();
        if (text.length() > 160) text = text.substring(0, 160);
        List<UUID> targets = new ArrayList<>();
        if (a[0].equalsIgnoreCase("all")) {
            for (UUID u : members) if (!tasks.containsKey(u)) targets.add(u);
        } else {
            String n = a[0];
            if (boss != null && nameOf(boss).equalsIgnoreCase(n)) {
                err(p, "You can't task the Boss.");
                return;
            }
            if (taskmaster != null && nameOf(taskmaster).equalsIgnoreCase(n)) {
                err(p, "The Taskmaster can't be tasked.");
                return;
            }
            UUID u = findMember(n);
            if (u == null) {
                err(p, "<white>" + esc(n) + "</white> isn't a member of your family.");
                return;
            }
            if (tasks.containsKey(u)) {
                err(p, nameOf(u) + " already has a task. Let them finish it first.");
                return;
            }
            targets.add(u);
        }
        if (targets.isEmpty()) {
            err(p, "Nobody to task right now.");
            return;
        }
        for (UUID u : targets) {
            tasks.put(u, text);
            Player t = Bukkit.getPlayer(u);
            if (t != null) {
                t.sendMessage(MM.deserialize("\n" + P + "<gold>New task from <yellow>" + p.getName() + "<gold>:\n  <white><task>\n"
                        + "<gray>When it's done, type <green>/taskcomplete<gray>.\n", Placeholder.unparsed("task", text)));
                t.showTitle(Title.title(MM.deserialize("<gold><bold>NEW TASK"), MM.deserialize("<gray>Check your chat.")));
                t.playSound(t.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 0.8f);
            }
        }
        save();
        send(p, "<green>Task sent to <white>" + (targets.size() == 1 ? nameOf(targets.get(0)) : targets.size() + " members") + "<green>.");
    }

    private void taskComplete(CommandSender s) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return;
        }
        UUID id = p.getUniqueId();
        if (!members.contains(id)) {
            err(p, "Only regular family members take tasks.");
            return;
        }
        String task = tasks.get(id);
        if (task == null) {
            err(p, "You don't have a task.");
            return;
        }
        if (!awaiting.add(id)) {
            err(p, "You already reported in. Wait for the verdict.");
            return;
        }
        save();
        send(p, "<yellow>Report sent. <gray>If the Taskmaster isn't satisfied... <red>you know what happens.");
        UUID rv = reviewerId();
        Player r = rv == null ? null : Bukkit.getPlayer(rv);
        if (r != null) sendReview(r, p.getName(), task);
        else send(p, "<gray>(Your reviewer is offline. They'll see it when they're back.)");
    }

    private void sendReview(Player r, String name, String task) {
        r.sendMessage(MM.deserialize("\n" + P + "<yellow>" + name + " <gray>says the task is done: <white><task>\n"
                        + "  <click:run_command:'/mafia taskaccept " + name + "'><hover:show_text:'<green>Approve and reward them'><green><bold>[ ACCEPT ]</bold></green></hover></click>"
                        + "   <click:run_command:'/mafia taskdecline " + name + "'><hover:show_text:'<red>They get killed, cleared and kicked out'><red><bold>[ DECLINE ]</bold></red></hover></click>\n",
                Placeholder.unparsed("task", task)));
        r.playSound(r.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
    }

    private void review(Player p, String[] a, boolean ok) {
        if (!isReviewer(p.getUniqueId())) {
            err(p, "Only the Taskmaster or the Boss can judge tasks.");
            return;
        }
        if (a.length < 2) {
            err(p, "Usage: /mafia " + (ok ? "taskaccept" : "taskdecline") + " <member>");
            return;
        }
        UUID t = findMember(a[1]);
        if (t == null || !awaiting.contains(t)) {
            err(p, "Nobody called <white>" + esc(a[1]) + "</white> is waiting for a verdict.");
            return;
        }
        tasks.remove(t);
        awaiting.remove(t);
        String n = nameOf(t);
        Player tp = Bukkit.getPlayer(t);
        if (ok) {
            save();
            send(p, "<green>Task approved for <white>" + n + "<green>.");
            if (tp != null) {
                send(tp, "<green>Your task was approved. <gray>The family is proud.");
                tp.playSound(tp.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                int cash = getConfig().getInt("task-reward-emeralds", 8);
                if (cash > 0) {
                    ItemStack money = new ItemStack(Material.EMERALD, cash);
                    ItemMeta m = money.getItemMeta();
                    m.displayName(L("<green><bold>Mafia Cash"));
                    money.setItemMeta(m);
                    giveOrDrop(tp, money);
                }
            }
        } else {
            send(p, "<red>Task declined. <gray>" + n + " is finished.");
            execute(t, n);
        }
    }

    // ----------------------------------------------------------- execution

    private void execute(UUID id, String name) {
        Player t = Bukkit.getPlayer(id);
        removeFromFamily(id);
        Bukkit.broadcast(MM.deserialize(P + "<dark_red>☠ <red>" + name + " <gray>failed the family and was <dark_red>dealt with<gray>."));
        if (t != null) {
            doExecution(t);
        } else {
            pendingExec.add(id);
            save();
        }
    }

    private void doExecution(Player t) {
        executed.add(t.getUniqueId());
        removeFamilyEffects(t);
        PlayerInventory inv = t.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(new ItemStack(Material.AIR));
        t.sendMessage(MM.deserialize(P + "<dark_red>You failed the family."));
        t.setHealth(0.0);
    }

    // --------------------------------------------------------------- admin

    private void forceJoin(CommandSender s, String[] a, Role role) {
        if (!s.hasPermission("mafia.admin")) {
            err(s, "No permission.");
            return;
        }
        Player t = null;
        if (a.length >= 1) t = Bukkit.getPlayerExact(a[0]);
        else if (s instanceof Player sp) t = sp;
        if (t == null) {
            String cmdName = role == Role.BOSS ? "forcejoinboss" : role == Role.TASKMASTER ? "forcejointaskmaster" : "forcejoinmember";
            err(s, "Player not found (they must be online). Usage: /" + cmdName + " <player>");
            return;
        }
        UUID id = t.getUniqueId();
        switch (role) {
            case BOSS -> {
                if (id.equals(boss)) {
                    err(s, t.getName() + " is already the Boss.");
                    return;
                }
                if (boss != null) setRole(boss, Role.MEMBER);
                becomeBoss(t);
            }
            case TASKMASTER -> {
                if (id.equals(boss)) {
                    err(s, t.getName() + " is the Boss and can't be Taskmaster.");
                    return;
                }
                if (taskmaster != null && !taskmaster.equals(id)) setRole(taskmaster, Role.MEMBER);
                setRole(id, Role.TASKMASTER);
                send(t, "<gold>An admin made you Taskmaster.");
            }
            case MEMBER -> {
                if (id.equals(boss)) {
                    err(s, t.getName() + " is the Boss.");
                    return;
                }
                setRole(id, Role.MEMBER);
                send(t, "<gray>An admin put you in the family.");
            }
        }
        send(s, "<green>" + t.getName() + " <gray>is now <white>" + roleOf(id).name().toLowerCase() + "<gray>.");
    }

    private void giveHat(CommandSender s, String[] a) {
        if (!s.hasPermission("mafia.admin")) {
            err(s, "No permission.");
            return;
        }
        Player t = null;
        if (a.length >= 1) t = Bukkit.getPlayerExact(a[0]);
        else if (s instanceof Player sp) t = sp;
        if (t == null) {
            err(s, "Player not found. Usage: /mafiahat [player]");
            return;
        }
        giveOrDrop(t, hatItem());
        send(s, "<gray>Gave a <white>Mafia Hat<gray> to <white>" + t.getName() + "<gray>.");
    }

    private void reset(CommandSender s) {
        if (!s.hasPermission("mafia.admin")) {
            err(s, "No permission.");
            return;
        }
        disband();
        pendingExec.clear();
        save();
        send(s, "<gray>The family has been wiped.");
    }

    private void familyChat(CommandSender s, String[] a) {
        if (!(s instanceof Player p) || !inFamily(p.getUniqueId())) {
            err(s, "Only family members can use the family line.");
            return;
        }
        if (a.length == 0) {
            err(p, "Usage: /mafiachat <message>");
            return;
        }
        Role r = roleOf(p.getUniqueId());
        String color = r == Role.BOSS ? "<dark_red>" : r == Role.TASKMASTER ? "<gold>" : "<gray>";
        Component msg = MM.deserialize("<dark_gray>[<dark_red>Family<dark_gray>] " + color + p.getName() + "<dark_gray>: <white><m>",
                Placeholder.unparsed("m", String.join(" ", a)));
        for (UUID u : allFamily()) {
            Player t = Bukkit.getPlayer(u);
            if (t != null) t.sendMessage(msg);
        }
        getLogger().info("[FamilyChat] " + p.getName() + ": " + String.join(" ", a));
    }

    // ---------------------------------------------------------- tab complete

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String n = c.getName().toLowerCase();
        if (n.equals("mafia")) {
            if (a.length == 1) {
                out.addAll(List.of("join", "info", "leave", "loadout", "accept", "decline", "appoint", "demote",
                        "kick", "resign", "taskaccept", "taskdecline"));
            } else if (a.length == 2) {
                switch (a[0].toLowerCase()) {
                    case "accept", "decline" -> {
                        long now = System.currentTimeMillis();
                        for (Map.Entry<UUID, Long> e : joinRequests.entrySet()) {
                            Player rp = Bukkit.getPlayer(e.getKey());
                            if (rp != null && e.getValue() > now) out.add(rp.getName());
                        }
                    }
                    case "appoint", "taskaccept", "taskdecline" -> members.forEach(u -> out.add(nameOf(u)));
                    case "kick" -> {
                        members.forEach(u -> out.add(nameOf(u)));
                        if (taskmaster != null) out.add(nameOf(taskmaster));
                    }
                    case "resign" -> out.add("confirm");
                    default -> { }
                }
            }
        } else if (n.equals("taskmafia") && a.length == 1) {
            out.add("all");
            members.forEach(u -> out.add(nameOf(u)));
        } else if ((n.startsWith("forcejoin") || n.equals("mafiahat")) && a.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }

    // =====================================================================
    //  gun handling
    // =====================================================================

    private int getAmmo(UUID id, Gun g) {
        return ammo.computeIfAbsent(id, k -> new EnumMap<>(Gun.class)).getOrDefault(g, g.mag);
    }

    private void setAmmo(UUID id, Gun g, int v) {
        ammo.computeIfAbsent(id, k -> new EnumMap<>(Gun.class)).put(g, v);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action act = e.getAction();
        ItemStack it = e.getItem();
        if (it == null) return;
        Player p = e.getPlayer();

        // left-clicking a block with a gun in hand must not mine it (the swing fires the gun instead)
        if (act == Action.LEFT_CLICK_BLOCK) {
            if (gunOf(it) != null) e.setCancelled(true);
            return;
        }
        if (act != Action.RIGHT_CLICK_AIR && act != Action.RIGHT_CLICK_BLOCK) return;

        if (isHat(it)) {
            e.setCancelled(true);
            claimWithHat(p);
            return;
        }

        Gun g = gunOf(it);
        if (g == null) return;
        UUID id = p.getUniqueId();
        if (!inFamily(id)) return;
        int now = Bukkit.getCurrentTick();

        // opening a chest / door: don't treat it as aiming, and don't let the swing that follows fire the gun
        if (act == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType().isInteractable() && !p.isSneaking()) {
            lastBlockUse.put(id, now);
            return;
        }

        // Holding right-click makes the client repeat this event every few ticks, so that is how we know you're aiming.
        lastAim.put(id, now);
        int amp = g == Gun.SNIPER ? 5 : (g == Gun.ROCKET ? 2 : 1);   // sniper: big FOV zoom, nearly standing still
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 8, amp, false, false, false));
    }

    private boolean isAiming(Player p) {
        return Bukkit.getCurrentTick() - lastAim.getOrDefault(p.getUniqueId(), -100) <= 6
                && gunOf(p.getInventory().getItemInMainHand()) != null;
    }

    /** Left-click (an arm swing) fires the gun. */
    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer();
        Gun g = gunOf(p.getInventory().getItemInMainHand());
        if (g == null) return;
        UUID id = p.getUniqueId();
        if (!inFamily(id) || p.isDead()) return;
        if (lastBlockUse.getOrDefault(id, -100) == Bukkit.getCurrentTick()) return;   // that swing was a door/chest click
        startBurst(p, g);
    }

    private int burstOf(Gun g) {
        return switch (g) {
            case TOMMY -> 5;
            case AR -> 3;
            default -> 1;
        };
    }

    private void startBurst(Player p, Gun g) {
        UUID id = p.getUniqueId();
        if (bursts.containsKey(id)) return;
        int shots = burstOf(g);
        if (shots <= 1) {
            tryShoot(p, g);
            return;
        }
        BukkitTask t = new BukkitRunnable() {
            int fired = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || fired >= shots || gunOf(p.getInventory().getItemInMainHand()) != g
                        || getAmmo(id, g) <= 0) {
                    bursts.remove(id);
                    cancel();
                    return;
                }
                tryShoot(p, g);
                fired++;
            }
        }.runTaskTimer(this, 0L, Math.max(1, g.cooldown));
        bursts.put(id, t);
    }

    private void tryShoot(Player p, Gun g) {
        UUID id = p.getUniqueId();
        if (!inFamily(id) || p.isDead() || reloads.containsKey(id)) return;
        int now = Bukkit.getCurrentTick();
        if (now < nextFire.getOrDefault(id, 0)) return;
        int have = getAmmo(id, g);
        if (have <= 0) {
            p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.8f, 1.6f);
            startReload(p, g);
            return;
        }
        nextFire.put(id, now + g.cooldown);
        setAmmo(id, g, have - 1);
        if (g == Gun.ROCKET) fireRocket(p);
        else fireBullets(p, g);
        showAmmo(p, g);
        if (have - 1 <= 0) startReload(p, g);
    }

    private void showAmmo(Player p, Gun g) {
        int have = getAmmo(p.getUniqueId(), g);
        int len = Math.min(g.mag, 15);
        int filled = (int) Math.ceil(have * (double) len / g.mag);
        String bar = "<green>" + "█".repeat(filled) + "<dark_gray>" + "█".repeat(len - filled);
        p.sendActionBar(MM.deserialize(g.color + "<bold>" + g.display + "</bold> " + bar + " <white>" + have + "<gray>/" + g.mag));
    }

    private void startReload(Player p, Gun g) {
        UUID id = p.getUniqueId();
        if (reloads.containsKey(id) || getAmmo(id, g) >= g.mag) return;
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, SoundCategory.PLAYERS, 1f, 1.2f);
        BukkitTask t = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || gunOf(p.getInventory().getItemInMainHand()) != g) {
                    reloads.remove(id);
                    cancel();
                    return;
                }
                elapsed += 2;
                if (elapsed >= g.reload) {
                    setAmmo(id, g, g.mag);
                    reloads.remove(id);
                    p.getWorld().playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, SoundCategory.PLAYERS, 1f, 1.1f);
                    showAmmo(p, g);
                    cancel();
                    return;
                }
                int total = 12;
                int filled = elapsed * total / g.reload;
                p.sendActionBar(MM.deserialize("<yellow>Reloading <gray>" + "█".repeat(filled) + "<dark_gray>" + "█".repeat(total - filled)));
            }
        }.runTaskTimer(this, 0L, 2L);
        reloads.put(id, t);
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        Gun g = gunOf(e.getPlayer().getInventory().getItemInMainHand());
        if (g == null) return;
        e.setCancelled(true);
        if (inFamily(e.getPlayer().getUniqueId())) startReload(e.getPlayer(), g);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack dropped = e.getItemDrop().getItemStack();
        if (!isMafiaItem(dropped)) return;
        e.setCancelled(true);
        Gun g = gunOf(dropped);
        if (g != null && inFamily(e.getPlayer().getUniqueId())) startReload(e.getPlayer(), g);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        Gun g = gunOf(e.getPlayer().getInventory().getItem(e.getNewSlot()));
        if (g != null && inFamily(e.getPlayer().getUniqueId())) showAmmo(e.getPlayer(), g);
    }

    private void fireBullets(Player p, Gun g) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector base = eye.getDirection();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        Location muzzle = eye.clone().add(base.clone().multiply(1.1)).add(0, -0.2, 0);
        w.spawnParticle(Particle.FLAME, muzzle, 2, 0.03, 0.03, 0.03, 0.01);
        w.spawnParticle(Particle.SMOKE, muzzle, 3, 0.05, 0.05, 0.05, 0.01);
        w.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 1.6f, g.pitch);
        w.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, g.boom, g.pitch);

        double spread = g.spread;
        if (isAiming(p)) spread *= 0.15;
        else if (p.isSneaking()) spread *= 0.5;
        else if (p.isSprinting()) spread *= 1.8;

        for (int i = 0; i < g.pellets; i++) {
            Vector dir = base.clone();
            if (spread > 0) {
                dir.add(new Vector(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).multiply(spread)).normalize();
            }
            hitscan(p, g, eye, dir);
        }
    }

    private boolean validTarget(Player shooter, Entity en) {
        if (!(en instanceof LivingEntity) || en.equals(shooter) || en instanceof ArmorStand || en.isDead()) return false;
        if (en instanceof Player o) {
            if (o.getGameMode() == GameMode.SPECTATOR) return false;
            if (!getConfig().getBoolean("friendly-fire", false)
                    && inFamily(o.getUniqueId()) && inFamily(shooter.getUniqueId())) return false;
        }
        return true;
    }

    private void hitscan(Player p, Gun g, Location eye, Vector dir) {
        World w = p.getWorld();
        RayTraceResult r = w.rayTrace(eye, dir, g.range, FluidCollisionMode.NEVER, true, 0.12, en -> validTarget(p, en));
        Location end = r != null ? r.getHitPosition().toLocation(w) : eye.clone().add(dir.clone().multiply(g.range));
        trail(eye, end, g);
        if (r == null) return;

        Entity he = r.getHitEntity();
        if (he instanceof LivingEntity le) {
            double dist = eye.distance(end);
            boolean head = r.getHitPosition().getY() >= le.getEyeLocation().getY() - 0.25;
            double dmg = g.damage * (head ? 1.5 : 1.0);
            if (g == Gun.SHOTGUN) dmg *= Math.max(0.25, 1.0 - dist / g.range);
            le.setNoDamageTicks(0);
            inShot = true;
            try {
                le.damage(dmg, p);
            } finally {
                inShot = false;
            }

            Location hp = r.getHitPosition().toLocation(w);
            w.spawnParticle(Particle.BLOCK, hp, 10, 0.12, 0.12, 0.12, Bukkit.createBlockData(Material.REDSTONE_BLOCK));
            if (head) w.spawnParticle(Particle.CRIT, hp, 8, 0.2, 0.2, 0.2, 0.3);
            p.playSound(p.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.7f, head ? 1.8f : 1.2f);
            if (le.isDead()) p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.6f);
        } else if (r.getHitBlock() != null) {
            w.spawnParticle(Particle.BLOCK, r.getHitPosition().toLocation(w), 6, 0.1, 0.1, 0.1, r.getHitBlock().getBlockData());
        }
    }

    private void trail(Location a, Location b, Gun g) {
        World w = a.getWorld();
        Vector v = b.toVector().subtract(a.toVector());
        double len = v.length();
        if (len < 2) return;
        v.normalize();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(g.trail), 0.5f);
        double step = g == Gun.SNIPER ? 0.8 : 2.0;
        for (double d = 1.5; d < len; d += step) {
            w.spawnParticle(Particle.DUST, a.clone().add(v.clone().multiply(d)), 1, 0, 0, 0, 0, dust);
        }
    }

    // ------------------------------------------------------------- rockets

    private void fireRocket(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        w.playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 2f, 0.7f);
        w.playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.5f, 1.6f);

        Snowball sb = w.spawn(eye.clone().add(dir.clone().multiply(1.0)), Snowball.class, s -> {
            s.setItem(new ItemStack(Material.FIREWORK_ROCKET));
            s.setShooter(p);
            s.setGravity(false);
            s.setVelocity(dir.clone().multiply(2.0));
            s.getPersistentDataContainer().set(rocketKey, PersistentDataType.BYTE, (byte) 1);
        });

        new BukkitRunnable() {
            int life = 0;

            @Override
            public void run() {
                if (!sb.isValid()) {
                    cancel();
                    return;
                }
                Location l = sb.getLocation();
                w.spawnParticle(Particle.FLAME, l, 4, 0.05, 0.05, 0.05, 0.02);
                w.spawnParticle(Particle.SMOKE, l, 6, 0.1, 0.1, 0.1, 0.01);
                if (++life > 80) {
                    Location boom = sb.getLocation();
                    sb.remove();
                    explode(boom, p);
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        if (!pr.getPersistentDataContainer().has(rocketKey, PersistentDataType.BYTE)) return;
        Player shooter = pr.getShooter() instanceof Player s ? s : null;
        Location loc = pr.getLocation();
        if (e.getHitBlockFace() != null) loc.add(e.getHitBlockFace().getDirection().multiply(0.6));
        pr.remove();
        explode(loc, shooter);
    }

    private void explode(Location loc, Player shooter) {
        World w = loc.getWorld();
        w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
        w.spawnParticle(Particle.FLAME, loc, 60, 1.0, 1.0, 1.0, 0.12);
        w.spawnParticle(Particle.SMOKE, loc, 40, 1.2, 1.2, 1.2, 0.05);
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 4f, 0.8f);

        double radius = 5.0;
        boolean ff = getConfig().getBoolean("friendly-fire", false);
        for (Entity en : w.getNearbyEntities(loc, radius, radius, radius)) {
            if (!(en instanceof LivingEntity le) || en instanceof ArmorStand) continue;
            if (le instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR) continue;
            boolean self = shooter != null && le.equals(shooter);
            if (!self && !ff && shooter != null && le instanceof Player pl2
                    && inFamily(pl2.getUniqueId()) && inFamily(shooter.getUniqueId())) continue;

            Location center = le.getLocation().add(0, le.getHeight() / 2, 0);
            double d = center.distance(loc);
            if (d > radius) continue;
            double f = 1.0 - d / radius;
            double dmg = Math.max(2.0, Gun.ROCKET.damage * f) * (self ? 0.4 : 1.0);

            le.setNoDamageTicks(0);
            inShot = true;
            try {
                if (self || shooter == null) le.damage(dmg);
                else le.damage(dmg, shooter);
            } finally {
                inShot = false;
            }

            Vector kb = center.toVector().subtract(loc.toVector());
            if (kb.lengthSquared() < 0.01) kb = new Vector(0, 1, 0);
            else kb.normalize();
            kb.multiply(0.5 + 1.3 * f);
            kb.setY(Math.max(kb.getY(), 0.35 + 0.4 * f));
            le.setVelocity(le.getVelocity().add(kb));
        }
    }

    // =====================================================================
    //  other listeners
    // =====================================================================

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            if (pendingExec.remove(id)) {
                save();
                doExecution(p);
                return;
            }
            Role r = roleOf(id);
            if (r == null) {
                if (stripItems(p)) removeFamilyEffects(p);
                return;
            }
            if (!nameOf(id).equals(p.getName())) {
                names.put(id, p.getName());
                save();
            }
            syncTeam(id);
            if (needsLoadout(p, r)) giveLoadout(p, false);
            else applyEffects(p);

            String task = tasks.get(id);
            if (r == Role.MEMBER && task != null) {
                if (awaiting.contains(id)) send(p, "<gray>Your task report is still waiting on a verdict.");
                else p.sendMessage(MM.deserialize(P + "<gold>You still have a task: <white><task>", Placeholder.unparsed("task", task)));
            }
            if (id.equals(reviewerId()) && !awaiting.isEmpty()) {
                send(p, "<yellow>" + awaiting.size() + " <gray>task report(s) are waiting for your verdict. <yellow>/mafia info");
                for (UUID u : awaiting) {
                    String t = tasks.get(u);
                    if (t != null) sendReview(p, nameOf(u), t);
                }
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        BukkitTask a = bursts.remove(id);
        if (a != null) a.cancel();
        BukkitTask r = reloads.remove(id);
        if (r != null) r.cancel();
        joinRequests.remove(id);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        UUID id = p.getUniqueId();
        BukkitTask r = reloads.remove(id);
        if (r != null) r.cancel();
        if (executed.remove(id)) {
            e.getDrops().clear();
            e.setKeepInventory(false);
            e.setKeepLevel(false);
            e.deathMessage(MM.deserialize("<dark_red>☠ <red>" + p.getName() + " <gray>was executed by the Mafia."));
            return;
        }
        if (inFamily(id)) e.getDrops().removeIf(this::isMafiaItem);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!inFamily(p.getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline()) giveLoadout(p, false);
        }, 2L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!inShot && e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && e.getDamager() instanceof Player shooter && gunOf(shooter.getInventory().getItemInMainHand()) != null) {
            e.setCancelled(true);   // left-click with a gun fires it; it never pistol-whips
            return;
        }
        if (getConfig().getBoolean("friendly-fire", false)) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        Entity damager = e.getDamager();
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity src) damager = src;
        if (damager instanceof Player attacker && !attacker.equals(victim)
                && inFamily(attacker.getUniqueId()) && inFamily(victim.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    /** Mafia gear is soulbound: it can't be stashed in chests, anvils, etc. */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING || top.getType() == InventoryType.PLAYER) return;
        boolean clickedTop = e.getClickedInventory() != null && e.getClickedInventory().equals(top);
        if (clickedTop && isMafiaItem(e.getCursor())) {
            e.setCancelled(true);
            return;
        }
        if (e.isShiftClick() && !clickedTop && isMafiaItem(e.getCurrentItem())) {
            e.setCancelled(true);
            return;
        }
        if (clickedTop && e.getClick() == ClickType.NUMBER_KEY && e.getWhoClicked() instanceof Player p) {
            ItemStack hot = p.getInventory().getItem(e.getHotbarButton());
            if (isMafiaItem(hot)) e.setCancelled(true);
        }
    }
}
