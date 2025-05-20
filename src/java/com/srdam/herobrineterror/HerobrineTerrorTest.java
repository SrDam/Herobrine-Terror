package com.srdam.herobrineterror;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCClickEvent;
import net.citizensnpcs.api.event.NPCDamageByEntityEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class HerobrineTerror extends JavaPlugin implements Listener, TabExecutor {

    private final Set<String> disabledPlayers = new HashSet<>();
    private final Random random = new Random();
    private final Map<String, Long> lastPresenceMessageTime = new HashMap<>();
    private final Set<Integer> herobrineNPCs = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadDisabledPlayers();

        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("herobrine")).setExecutor(this);

        Bukkit.getScheduler().runTaskTimer(this, () -> spawnHerobrine(null), 0L, 20L * getConfig().getInt("tiempo-entre-apariciones-minutos", 3) * 60);
    }

    private void loadDisabledPlayers() {
        disabledPlayers.clear();
        List<String> lista = getConfig().getStringList("jugadores-desactivados");
        for (String nombre : lista) {
            disabledPlayers.add(nombre.toLowerCase());
        }
    }

    private void spawnHerobrine(Player forcedTarget) {
        double prob = getConfig().getDouble("probabilidad-aparicion", 0.3);
        if (forcedTarget == null && random.nextDouble() > prob) return;

        List<Player> players = new ArrayList<>();
        List<String> mundos = getConfig().getStringList("mundos-habilitados");

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!disabledPlayers.contains(p.getName().toLowerCase()) &&
                mundos.contains(p.getWorld().getName())) {
                players.add(p);
            }
        }

        if (players.isEmpty()) return;

        Player target = forcedTarget != null ? forcedTarget : players.get(random.nextInt(players.size()));

        Location targetLoc = target.getLocation();
        Location behind = targetLoc.clone().add(targetLoc.getDirection().multiply(-2)).add(0, 1, 0);

        if (!behind.getBlock().isPassable()) {
            behind = targetLoc.clone().add(targetLoc.getDirection().multiply(-3)).add(0, 1, 0);
            if (!behind.getBlock().isPassable()) return;
        }

        Location spawnLoc = behind.clone();
        Vector directionToPlayer = targetLoc.toVector().subtract(spawnLoc.toVector()).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-directionToPlayer.getX(), directionToPlayer.getZ()));
        spawnLoc.setYaw(yaw);

        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "Herobrine");
        npc.spawn(spawnLoc);
        npc.setProtected(true);
        herobrineNPCs.add(npc.getId());

        SkinTrait skin = npc.getOrAddTrait(SkinTrait.class);
		skin.setSkinName(getConfig().getString("skin-name", "noaarc"));

        LookClose lookClose = npc.getOrAddTrait(LookClose.class);
        try {
            npc.data().setPersistent("lookclose", true);
        } catch (Exception ignored) {}

        target.playSound(target.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1.0f, 1.0f);

        Bukkit.getScheduler().runTaskTimer(this, task -> {
            if (!npc.isSpawned() || !target.isOnline()) {
                destroyHerobrine(npc);
                task.cancel();
                return;
            }

            Location playerLoc = target.getEyeLocation();
            Location npcLoc = npc.getEntity().getLocation();
            Vector dirToNpc = npcLoc.toVector().subtract(playerLoc.toVector()).normalize();
            Vector playerDir = playerLoc.getDirection().normalize();

            if (dirToNpc.dot(playerDir) > 0.95) {
                scarePlayer(target, npc);
                destroyHerobrine(npc);
                task.cancel();
            } else {
                long now = System.currentTimeMillis();
                String playerName = target.getName().toLowerCase();
                if (!lastPresenceMessageTime.containsKey(playerName) || now - lastPresenceMessageTime.get(playerName) > 10000) {
                    target.sendMessage(ChatColor.DARK_RED + getConfig().getString("mensajes.presencia", "ꜱɪᴇɴᴛᴇꜱ ᴜɴᴀ ᴘʀᴇꜱᴇɴᴄɪᴀ ᴍᴀʟɪɢɴᴀ, ᴍɪʀᴀ ᴀ ᴛᴜ ᴇꜱᴘᴀʟᴅᴀ ꜱɪ ᴛᴇ ᴀᴛʀᴇᴠᴇꜱ...."));
                    target.playSound(target.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.6f);
                    lastPresenceMessageTime.put(playerName, now);
                }
            }
        }, 0L, 5L);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (npc.isSpawned()) destroyHerobrine(npc);
        }, 100L);
    }

    private void scarePlayer(Player player, NPC npc) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 1));
        player.sendMessage(ChatColor.YELLOW + getConfig().getString("mensajes.scare", "ʜᴇʀᴏʙʀɪɴᴇ: ᴛᴜ ɴᴏ ᴠɪꜱᴛᴇ ɴᴀᴅᴀ"));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 1.0f, 0.5f);
    }

    private void destroyHerobrine(NPC npc) {
        if (!npc.isSpawned()) return;

        Location loc = npc.getEntity().getLocation();
        boolean hasNearbyVillagers = loc.getWorld().getNearbyEntities(loc, 5, 5, 5).stream()
                .anyMatch(e -> e.getType() == EntityType.VILLAGER);

        if (!hasNearbyVillagers) {
            loc.getWorld().strikeLightningEffect(loc);
        }

        npc.destroy();
        herobrineNPCs.remove(npc.getId());
    }

    @EventHandler
    public void onNPCHit(NPCDamageByEntityEvent event) {
        if (herobrineNPCs.contains(event.getNPC().getId()) && event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            scarePlayer(player, event.getNPC());
            destroyHerobrine(event.getNPC());
        }
    }

    @EventHandler
    public void onNPCClick(NPCClickEvent event) {
        if (herobrineNPCs.contains(event.getNPC().getId()) && event.getClicker() != null) {
            scarePlayer(event.getClicker(), event.getNPC());
            destroyHerobrine(event.getNPC());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target != null) {
                spawnHerobrine(target);
                sender.sendMessage(ChatColor.GREEN + getConfig().getString("mensajes.test-lanzado", "Herobrine test lanzado para ") + target.getName());
            } else {
                sender.sendMessage(ChatColor.RED + getConfig().getString("mensajes.jugador-no-encontrado", "Jugador no encontrado."));
            }
            return true;
        }

        if (args.length >= 2 && (args[0].equalsIgnoreCase("active") || args[0].equalsIgnoreCase("desactive"))) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + getConfig().getString("mensajes.jugador-no-encontrado", "Jugador no encontrado."));
                return true;
            }
            String nombreMinuscula = target.getName().toLowerCase();
            if (args[0].equalsIgnoreCase("active")) {
                disabledPlayers.remove(nombreMinuscula);
                sender.sendMessage(ChatColor.GREEN + getConfig().getString("mensajes.activado", "Herobrine activado para ") + target.getName());
            } else {
                disabledPlayers.add(nombreMinuscula);
                sender.sendMessage(ChatColor.RED + getConfig().getString("mensajes.desactivado", "Herobrine desactivado para ") + target.getName());
            }
            saveDisabledPlayersToConfig();
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadDisabledPlayers();
            sender.sendMessage(ChatColor.GREEN + getConfig().getString("mensajes.reload", "Configuración de Herobrine recargada."));
            return true;
        }

        return false;
    }

    private void saveDisabledPlayersToConfig() {
        List<String> lista = new ArrayList<>(disabledPlayers);
        getConfig().set("jugadores-desactivados", lista);
        saveConfig();
    }
}
