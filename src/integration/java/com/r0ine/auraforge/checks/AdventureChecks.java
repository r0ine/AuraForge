package com.r0ine.auraforge.checks;

import com.r0ine.auraforge.AuraForgePlugin;
import com.r0ine.auraforge.adventure.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.nio.file.Files;
import java.util.*;

public final class AdventureChecks extends JavaPlugin implements Listener {
    private AuraForgePlugin forge;
    private boolean denyDamage;
    private int assertions;

    @Override public void onEnable() {
        forge = (AuraForgePlugin) getServer().getPluginManager().getPlugin("AuraForge");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler public void joined(PlayerJoinEvent event) {
        if (!event.getPlayer().getName().equals("ForgeVerifier")) return;
        getServer().getScheduler().runTaskLater(this, () -> run(event.getPlayer()), 40);
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void protect(EntityDamageByEntityEvent event) {
        if (denyDamage) event.setCancelled(true);
    }

    private void run(Player player) {
        try {
            if (Files.exists(getDataFolder().toPath().resolve("passed.txt"))) {
                var profile = forge.getAdventure().profile(player);
                require(profile.getEssence() == 777 && profile.getXp() == 1900, "Oyuncu kaydı yeniden başlatmada korunuyor");
                require(profile.getStudied().size() == 10 && "eclipse".equals(profile.getSelected()), "Büyüler yeniden yükleniyor");
                require("blaze".equals(profile.getContract()) && profile.getContractKills() == 3, "Sözleşme yeniden yükleniyor");
                require(profile.getMilestones().contains("blaze:10"), "Günlük yeniden yükleniyor");
                require(profile.getCooldowns().getOrDefault("eclipse", 0L) > System.currentTimeMillis(), "Bekleme yeniden başlatmada korunuyor");
                require(forge.getAdventure().traits(player.getInventory().getItemInMainHand()).contains(WeaponTrait.EMBER), "Eşya mührü yeniden başlatmada korunuyor");
                getLogger().info("ADVENTURE_PERSISTENCE_PASSED " + assertions);
                return;
            }
            player.setOp(true);
            player.setGameMode(GameMode.SURVIVAL);
            player.teleport(new Location(player.getWorld(), 0.5, 81, 0.5, 0, 0));
            for (int x = -10; x <= 10; x++) for (int z = -10; z <= 20; z++) {
                player.getWorld().getBlockAt(x, 80, z).setType(Material.STONE);
                for (int y = 81; y <= 85; y++) player.getWorld().getBlockAt(x, y, z).setType(Material.AIR);
            }
            seed(player, 1900, 10000, Set.of(), null, null, 0, Map.of(), Set.of());
            checkMenus(player);
            checkTraits(player);
            checkSets(player);
            checkResearch(player);
            checkContracts(player);
            checkSalvage(player);
            checkSpells(player);
            checkProtection(player);
            player.closeInventory();
            player.setVelocity(new org.bukkit.util.Vector());
            getServer().getScheduler().runTaskLater(this, () -> checkVanillaAttack(player), 30);
        } catch (Throwable failure) {
            getLogger().log(java.util.logging.Level.SEVERE, "ADVENTURE_CHECKS_FAILED", failure);
        }
    }

    private ItemStack weapon(Player player) {
        var equipment = forge.getItems().create(forge.getRecipes().get("ruzgar-kilici"), 80, "Kontrol");
        player.getInventory().setItemInMainHand(equipment);
        return equipment;
    }

    private Pillager target(Player player) {
        var zombie = player.getWorld().spawn(player.getLocation().add(0, 0, 4), Pillager.class);
        zombie.setAI(false);
        zombie.setSilent(true);
        zombie.getAttribute(Attribute.MAX_HEALTH).setBaseValue(100);
        zombie.setHealth(100);
        zombie.setFireTicks(0);
        return zombie;
    }

    private void checkMenus(Player player) {
        weapon(player);
        for (var page : AdventurePage.values()) {
            forge.getAdventureMenus().open(player, page);
            require(player.getOpenInventory().getTopInventory().getSize() == 54, "Menü açılıyor: " + page);
            for (var click : List.of(ClickType.SHIFT_LEFT, ClickType.NUMBER_KEY, ClickType.DOUBLE_CLICK, ClickType.DROP)) {
                var event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, 13, click, InventoryAction.PICKUP_ALL);
                getServer().getPluginManager().callEvent(event);
                require(event.isCancelled(), "Menü eşya koruması: " + page + "/" + click);
            }
        }
        forge.getProgressMenus().openHub(player, null);
        require(player.getOpenInventory().getTopInventory().getItem(38).getType() == Material.COMPASS, "Gelişim menüsünde macera düğmesi");
        require(player.getOpenInventory().getTopInventory().getItem(40).getType() == Material.WRITABLE_BOOK, "Eski kodeks düğmesi korunuyor");
        player.closeInventory();
    }

    private void checkTraits(Player player) {
        var service = forge.getAdventure();
        for (var trait : WeaponTrait.values()) {
            weapon(player);
            int essence = service.profile(player).getEssence();
            service.inscribe(player, trait);
            var held = player.getInventory().getItemInMainHand();
            require(service.traits(held).contains(trait), "Mühür işleniyor: " + trait);
            require(service.profile(player).getEssence() == essence - trait.getCost(), "Mühür bedeli: " + trait);
            require(service.traits(ItemStack.deserializeBytes(held.serializeAsBytes())).contains(trait), "Mühür PDC kaydı: " + trait);
            var victim = target(player);
            if (trait == WeaponTrait.EXECUTION) victim.setHealth(20);
            if (trait == WeaponTrait.SUNDER) victim.getAttribute(Attribute.ARMOR).setBaseValue(20);
            if (trait == WeaponTrait.FROSTBITE) victim.setFreezeTicks(60);
            if (trait == WeaponTrait.KINDLING) victim.setFireTicks(60);
            player.setHealth(10);
            var hit = new EntityDamageByEntityEvent(player, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10);
            getServer().getPluginManager().callEvent(hit);
            switch (trait) {
                case EMBER -> require(victim.getFireTicks() >= 60, "Kor yakıyor");
                case FROST -> require(victim.hasPotionEffect(PotionEffectType.SLOWNESS), "Kırağı yavaşlatıyor");
                case VENOM -> require(victim.hasPotionEffect(PotionEffectType.POISON), "Zehir işliyor");
                case RUPTURE -> require(victim.hasPotionEffect(PotionEffectType.WITHER), "Yırtılma işliyor");
                case VAMPIRIC -> require(player.getHealth() > 10, "Can emme işliyor");
                case REPEL -> require(victim.getVelocity().getZ() > 0, "Savuruş itiyor");
                case GRAVITY -> require(victim.getVelocity().getY() < 0, "Ağırlık bastırıyor");
                case REAPER -> {
                    int before = service.profile(player).getEssence();
                    victim.setNoDamageTicks(0);
                    victim.damage(1000, player);
                    require(service.profile(player).getEssence() == before + 4, "Ruh Hasadı ödülü");
                }
                case ARCANE_LEECH -> require(service.profile(player).getMana() > 0, "Mana yenileniyor");
                case EXECUTION, AMBUSH, GIANTSLAYER, SUNDER, MOMENTUM, BACKSTAB, AERIAL, FROSTBITE, KINDLING, HUNTER, FOCUS ->
                    require(hit.getDamage() > 10, "Koşullu hasar: " + trait);
            }
            victim.remove();
            service.inscribe(player, trait);
            require(service.traits(player.getInventory().getItemInMainHand()).isEmpty(), "Mühür çıkarılıyor: " + trait);
            require(service.profile(player).getEssence() <= essence, "Çıkarma öz üretmiyor");
        }
        weapon(player);
        service.inscribe(player, WeaponTrait.EMBER);
        require(service.traitLock(player, WeaponTrait.FROST) != null, "Dolu mühür yuvası reddediliyor");
        player.performCommand("af set level 10");
        service.inscribe(player, WeaponTrait.FROST);
        require(service.traits(player.getInventory().getItemInMainHand()).size() == 2, "Seviye 10 ikinci yuvayı açıyor");
        require(service.traitLock(player, WeaponTrait.VENOM) != null, "Üçüncü yuva seviye 10'da kapalı");
        player.performCommand("af set level 20");
        service.inscribe(player, WeaponTrait.VENOM);
        require(service.traits(player.getInventory().getItemInMainHand()).size() == 3, "Seviye 20 üçüncü yuvayı açıyor");
        seed(player, 0, 0, Set.of(), null, null, 0, Map.of(), Set.of());
        weapon(player);
        service.inscribe(player, WeaponTrait.EMBER);
        require(service.traits(player.getInventory().getItemInMainHand()).isEmpty(), "Özsüz işlem reddediliyor");
        seed(player, 1900, 10000, Set.of(), null, null, 0, Map.of(), Set.of());
    }

    private void checkSets(Player player) {
        var recipes = List.of("kale-migferi", "kale-gogusluk", "firtina-tozlugu", "sicrayan-cizme");
        for (var set : ArmorSet.values()) {
            ItemStack[] armor = new ItemStack[4];
            for (int i = 0; i < 4; i++) {
                var equipment = forge.getItems().create(forge.getRecipes().get(recipes.get(i)), 80, "Kontrol");
                player.getInventory().setItemInMainHand(equipment);
                forge.getAdventure().bindSet(player, set);
                armor[3 - i] = player.getInventory().getItemInMainHand().clone();
            }
            player.getInventory().setArmorContents(armor);
            require(forge.getAdventure().pieces(player, set) == 4, "Set parça sayısı: " + set);
            var incoming = new EntityDamageEvent(player, set == ArmorSet.ASH ? EntityDamageEvent.DamageCause.FIRE : EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10);
            getServer().getPluginManager().callEvent(incoming);
            if (set == ArmorSet.ASH || set == ArmorSet.GLACIER || set == ArmorSet.EARTHEN) require(incoming.getDamage() < 10, "Set savunması: " + set);
            if (set == ArmorSet.ASTRAL) require(forge.getAdventure().manaMaximum(player) == 158, "Astral mana sınırı");
        }
        player.getInventory().setArmorContents(new ItemStack[4]);
        require(forge.getAdventure().pieces(player, ArmorSet.ASTRAL) == 0, "Çıkarılan set bonusu bitiyor");
    }

    private void checkResearch(Player player) {
        seed(player, 1900, 10000, Set.of(), null, null, 0, Map.of(), Set.of());
        var service = forge.getAdventure();
        service.research(player, LearnedSpell.ECLIPSE);
        require(!service.profile(player).getStudied().contains("eclipse"), "Öncülsüz araştırma reddediliyor");
        for (var spell : LearnedSpell.values()) {
            service.research(player, spell);
            require(service.profile(player).getStudied().contains(spell.getId()), "Büyü öğreniliyor: " + spell);
        }
        int essence = service.profile(player).getEssence();
        service.research(player, LearnedSpell.CINDER_LANCE);
        require(service.profile(player).getEssence() == essence, "Büyü seçmek ücretsiz");
        service.research(player, LearnedSpell.CINDER_LANCE);
        require(service.profile(player).getSelected() == null, "Büyü seçimi kaldırılıyor");
    }

    private void checkContracts(Player player) {
        var service = forge.getAdventure();
        seed(player, 1900, 100, Set.of(), null, "zombie", 5, Map.of("zombie", 75), Set.of());
        require(service.claimContract(player).contains("teslim"), "Sözleşme ödülü alınıyor");
        int earned = service.profile(player).getEssence();
        service.claimContract(player);
        require(service.profile(player).getEssence() == earned, "Sözleşme iki kez ödenmiyor");
        for (int i = 0; i < 3; i++) service.studyQuarry(player, Quarry.ZOMBIE);
        require(service.bestiaryBonus(player, Quarry.ZOMBIE) == 0.06, "Üç günlük eşiği birikiyor");
        earned = service.profile(player).getEssence();
        service.studyQuarry(player, Quarry.ZOMBIE);
        require(service.profile(player).getEssence() == earned, "Günlük ödülü tekrarlanmıyor");
    }

    private void checkSalvage(Player player) {
        var service = forge.getAdventure();
        var equipment = weapon(player).clone();
        int essence = service.profile(player).getEssence();
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIRT));
        service.salvage(player, equipment);
        require(service.profile(player).getEssence() == essence, "Değişen eşya sökülmüyor");
        player.getInventory().setItemInMainHand(equipment);
        service.salvage(player, equipment.clone());
        require(player.getInventory().getItemInMainHand().isEmpty(), "Sökülen eşya tüketiliyor");
        require(service.profile(player).getEssence() == essence + 12, "Söküm öz veriyor");
        service.salvage(player, equipment);
        require(service.profile(player).getEssence() == essence + 12, "Söküm tekrarlanmıyor");
    }

    private void checkSpells(Player player) {
        weapon(player);
        Set<String> studied = new HashSet<>();
        for (var spell : LearnedSpell.values()) studied.add(spell.getId());
        for (var spell : LearnedSpell.values()) {
            seed(player, 1900, 1000, studied, spell.getId(), null, 0, Map.of(), Set.of());
            player.setHealth(10);
            var victim = target(player);
            require(forge.getAdventureMagic().cast(player), "Büyü kullanılıyor: " + spell);
            var profile = forge.getAdventure().profile(player);
            require(profile.getCooldowns().containsKey(spell.getId()), "Büyü beklemesi: " + spell);
            require(profile.getMana() < forge.getAdventure().manaMaximum(player), "Mana harcanıyor: " + spell);
            switch (spell) {
                case STONE_SKIN -> require(player.hasPotionEffect(PotionEffectType.RESISTANCE), "Taş Deri etkisi");
                case VERDANT_PULSE -> require(player.getHealth() > 10, "Yaşam Filizi iyileşmesi");
                case WINDSTEP -> require(player.hasPotionEffect(PotionEffectType.SLOW_FALLING), "Rüzgâr İzleri etkisi");
                default -> require(victim.getHealth() < 100, "Büyü hasarı: " + spell);
            }
            double mana = profile.getMana();
            forge.getAdventureMagic().cast(player);
            require(forge.getAdventure().profile(player).getMana() == mana, "Beklemede mana harcanmıyor: " + spell);
            victim.remove();
        }
        seed(player, 1900, 1000, studied, "cinder_lance", null, 0, Map.of(), Set.of());
        var victim = target(player);
        denyDamage = true;
        forge.getAdventureMagic().cast(player);
        denyDamage = false;
        require(victim.getHealth() == 100 && victim.getFireTicks() == 0, "İptal edilen büyü hasar ve yanma vermiyor");
        victim.remove();
        seed(player, 1900, 777, studied, "eclipse", "blaze", 3, Map.of("blaze", 30), Set.of("blaze:10"));
    }

    private void checkProtection(Player player) {
        var service = forge.getAdventure();
        weapon(player);
        seed(player, 1900, 1000, Set.of("cinder_lance"), "cinder_lance", null, 0, Map.of(), Set.of());
        var victim = target(player);
        var wall = player.getWorld().getBlockAt(player.getLocation().add(0, 1, 2));
        wall.setType(Material.STONE);
        forge.getAdventureMagic().cast(player);
        require(service.profile(player).getCooldowns().isEmpty(), "Duvar hedefi engelliyor ve mana harcanmıyor");
        wall.setType(Material.AIR);
        var permission = player.addAttachment(this, "auraforge.cast", false);
        forge.getAdventureMagic().cast(player);
        require(service.profile(player).getCooldowns().isEmpty(), "Büyü yetkisi kontrol ediliyor");
        player.removeAttachment(permission);
        service.inscribe(player, WeaponTrait.EMBER);
        denyDamage = true;
        var rejected = new EntityDamageByEntityEvent(player, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10);
        getServer().getPluginManager().callEvent(rejected);
        denyDamage = false;
        require(victim.getFireTicks() == 0 && forge.getAdventureCombat().combo(player, victim) == 1, "İptal edilen saldırı mühür veya kombo üretmiyor");
        victim.remove();
        int before = service.profile(player).getEssence();
        var farmMob = player.getWorld().spawn(player.getLocation().add(0, 0, 4), Pillager.class,
            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER);
        farmMob.damage(1000, player);
        require(service.profile(player).getEssence() == before, "Spawner avı öz üretmiyor");
        farmMob.remove();
        Set<String> studied = new HashSet<>();
        for (var spell : LearnedSpell.values()) studied.add(spell.getId());
        var saved = new Adventurer(1900, 777, Map.of("blaze", 30), studied, "eclipse", "blaze", 3, 0,
            Set.of("blaze:10"), 42.0, System.currentTimeMillis(), Map.of("eclipse", System.currentTimeMillis() + 300000));
        service.load(Map.of(player.getUniqueId().toString(), saved));
    }

    private void seed(Player player, int xp, int essence, Set<String> studied, String selected, String contract, int kills, Map<String, Integer> journal, Set<String> milestones) {
        var profile = new Adventurer(xp, essence, journal, studied, selected, contract, kills, 0, milestones, 138.0, System.currentTimeMillis(), Map.of());
        forge.getAdventure().load(Map.of(player.getUniqueId().toString(), profile));
    }

    private void checkVanillaAttack(Player player) {
        var victim = target(player);
        victim.teleport(player.getLocation().add(0, 0, 2));
        player.sendMessage("FORGE_ATTACK_READY");
        getServer().getScheduler().runTaskLater(this, () -> {
            try {
                require(victim.getHealth() < 100, "İstemci saldırısı hasar veriyor");
                require(victim.getFireTicks() > 0, "İstemci saldırısında mühür tetikleniyor");
                require(forge.getAdventureCombat().combo(player, victim) == 2, "İstemci saldırısı kombo başlatıyor");
                var serialized = ItemStack.deserializeBytes(player.getInventory().getItemInMainHand().serializeAsBytes());
                require(forge.getItems().read(serialized) != null, "Silah yeniden okunabiliyor");
                getDataFolder().mkdirs();
                Files.writeString(getDataFolder().toPath().resolve("passed.txt"), "GEÇTİ: " + assertions + " sunucu doğrulaması\n");
                getLogger().info("ADVENTURE_CHECKS_PASSED " + assertions);
            } catch (Throwable failure) {
                getLogger().log(java.util.logging.Level.SEVERE, "ADVENTURE_CHECKS_FAILED", failure);
            } finally {
                victim.remove();
            }
        }, 30);
    }

    private void require(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
}
