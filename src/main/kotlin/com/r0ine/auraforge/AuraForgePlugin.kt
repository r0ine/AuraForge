package com.r0ine.auraforge

import com.r0ine.auraforge.ability.AbilityItems
import com.r0ine.auraforge.ability.AbilityListener
import com.r0ine.auraforge.ability.AbilityService
import com.r0ine.auraforge.arcane.AltarListener
import com.r0ine.auraforge.arcane.AltarManager
import com.r0ine.auraforge.arcane.AltarMenus
import com.r0ine.auraforge.command.AuraForgeCommand
import com.r0ine.auraforge.core.Keys
import com.r0ine.auraforge.core.SaveFile
import com.r0ine.auraforge.core.Settings
import com.r0ine.auraforge.core.SettingsLoader
import com.r0ine.auraforge.core.Storage
import com.r0ine.auraforge.core.Text
import com.r0ine.auraforge.core.rich
import com.r0ine.auraforge.core.tag
import com.r0ine.auraforge.crafting.CraftingService
import com.r0ine.auraforge.crafting.RecipeBook
import com.r0ine.auraforge.crafting.SmithStats
import com.r0ine.auraforge.forge.ForgeCore
import com.r0ine.auraforge.forge.ForgeListener
import com.r0ine.auraforge.forge.ForgeManager
import com.r0ine.auraforge.menu.ForgeMenus
import com.r0ine.auraforge.spell.SpellEffects
import com.r0ine.auraforge.spell.SpellItems
import com.r0ine.auraforge.spell.SpellListener
import com.r0ine.auraforge.spell.SpellService
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.kyori.adventure.audience.Audience
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

class AuraForgePlugin : JavaPlugin() {

    lateinit var scope: CoroutineScope
        private set

    @Volatile
    var settings = Settings()
        private set

    lateinit var keys: Keys
        private set
    lateinit var text: Text
        private set
    lateinit var recipes: RecipeBook
        private set
    lateinit var forges: ForgeManager
        private set
    lateinit var core: ForgeCore
        private set
    lateinit var items: AbilityItems
        private set
    lateinit var abilities: AbilityService
        private set
    lateinit var crafting: CraftingService
        private set
    lateinit var smiths: SmithStats
        private set
    lateinit var menus: ForgeMenus
        private set
    lateinit var spellEffects: SpellEffects
        private set
    lateinit var spellItems: SpellItems
        private set
    lateinit var spells: SpellService
        private set
    lateinit var altars: AltarManager
        private set
    lateinit var altarMenus: AltarMenus
        private set

    private lateinit var storage: Storage
    private val dirty = AtomicBoolean(false)

    override fun onEnable() {
        scope = CoroutineScope(
            SupervisorJob() + CoroutineName("AuraForge") + CoroutineExceptionHandler { _, error ->
                logger.log(Level.SEVERE, "Arka plan görevi hata verdi", error)
            },
        )
        keys = Keys(this)
        text = Text(this)
        recipes = RecipeBook()
        spellEffects = SpellEffects(this)
        spellItems = SpellItems(this)
        spells = SpellService(this)
        if (!reloadSettings()) logger.warning("Varsayılan ayarlarla devam ediliyor.")

        forges = ForgeManager(this)
        core = ForgeCore(this)
        items = AbilityItems(this)
        abilities = AbilityService(this)
        crafting = CraftingService(this)
        smiths = SmithStats()
        menus = ForgeMenus(this)
        altars = AltarManager(this)
        altarMenus = AltarMenus(this)

        storage = Storage(File(dataFolder, "data.json"), logger)
        val saved = storage.load()
        forges.load(saved.forges)
        altars.load(saved.altars)
        smiths.load(saved.smiths)

        if (settings.forge.coreRecipe) core.registerRecipe()
        spellItems.registerRecipes()

        listOf(
            ForgeListener(this),
            AbilityListener(this),
            crafting,
            menus,
            AltarListener(this),
            altarMenus,
            SpellListener(this),
        ).forEach { server.pluginManager.registerEvents(it, this) }

        getCommand("auraforge")?.let { command ->
            val executor = AuraForgeCommand(this)
            command.setExecutor(executor)
            command.tabCompleter = executor
        }

        forges.showLoaded()
        altars.showLoaded()
        startAutosave()
        logger.info("${forges.all().size} tezgah, ${altars.all().size} masa, ${recipes.all.size} tarif ve ${spells.book.all.size} büyü yüklendi.")
    }

    override fun onDisable() {
        if (!::scope.isInitialized) return
        scope.cancel()
        if (::core.isInitialized) core.unregisterRecipe()
        if (::spellItems.isInitialized) spellItems.unregisterRecipes()
        if (::storage.isInitialized) {
            runCatching { save() }.onFailure { logger.log(Level.SEVERE, "Veriler kaydedilemedi", it) }
        }
    }

    fun reloadSettings(): Boolean = try {
        settings = SettingsLoader.load(this)
        recipes.load(settings.recipes, settings.forge.maxItems, logger)
        spells.book.load(settings.spells, logger)
        true
    } catch (error: Exception) {
        logger.log(Level.SEVERE, "config.yml okunamadı: ${error.message}")
        false
    }

    fun markDirty() = dirty.set(true)

    fun sendRecipes(to: Audience) {
        val all = recipes.all
        if (all.isEmpty()) return text.send(to, "recipes-empty")
        text.send(to, "recipes-header")
        all.forEach { recipe ->
            text.send(
                to,
                "recipe-line",
                rich("name", recipe.name),
                tag("ability", text.render("ability-${recipe.ability.key}")),
                tag("ingredients", recipe.ingredientList()),
            )
        }
    }

    fun sendSpells(to: Audience) {
        val all = spells.book.all
        if (all.isEmpty()) return text.send(to, "spells-empty")
        text.send(to, "spells-header")
        all.forEach { spell ->
            text.send(
                to,
                "spell-line",
                rich("name", spell.name),
                tag("elements", spell.elements.joinToString(" <dark_gray>+</dark_gray> ") {
                    "<color:#${String.format("%06X", it.rgb)}>${it.display}</color>"
                }),
            )
        }
    }

    private fun startAutosave() {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(settings.autosaveSeconds * 1000L)
                if (!dirty.getAndSet(false)) continue
                runCatching { save() }.onFailure {
                    markDirty()
                    logger.log(Level.WARNING, "Otomatik kayıt başarısız", it)
                }
            }
        }
    }

    private fun save() {
        storage.write(SaveFile(forges = forges.records(), altars = altars.records(), smiths = smiths.snapshot()))
    }
}
