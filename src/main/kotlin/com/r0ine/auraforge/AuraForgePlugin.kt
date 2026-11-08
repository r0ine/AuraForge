package com.r0ine.auraforge

import com.r0ine.auraforge.ability.AbilityItems
import com.r0ine.auraforge.ability.AbilityListener
import com.r0ine.auraforge.ability.AbilityService
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
        if (!reloadSettings()) logger.warning("Varsayılan ayarlarla devam ediliyor.")

        forges = ForgeManager(this)
        core = ForgeCore(this)
        items = AbilityItems(this)
        abilities = AbilityService(this)
        crafting = CraftingService(this)
        smiths = SmithStats()
        menus = ForgeMenus(this)

        storage = Storage(File(dataFolder, "data.json"), logger)
        val saved = storage.load()
        forges.load(saved.forges)
        smiths.load(saved.smiths)

        if (settings.forge.coreRecipe) core.registerRecipe()

        listOf(ForgeListener(this), AbilityListener(this), crafting, menus)
            .forEach { server.pluginManager.registerEvents(it, this) }

        getCommand("auraforge")?.let { command ->
            val executor = AuraForgeCommand(this)
            command.setExecutor(executor)
            command.tabCompleter = executor
        }

        forges.showLoaded()
        startAutosave()
        logger.info("${forges.all().size} tezgah ve ${recipes.all.size} tarif yüklendi.")
    }

    override fun onDisable() {
        if (!::scope.isInitialized) return
        scope.cancel()
        if (::core.isInitialized) core.unregisterRecipe()
        if (::storage.isInitialized) {
            runCatching { save() }.onFailure { logger.log(Level.SEVERE, "Veriler kaydedilemedi", it) }
        }
    }

    fun reloadSettings(): Boolean = try {
        settings = SettingsLoader.load(this)
        recipes.load(settings.recipes, settings.forge.maxItems, logger)
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
        storage.write(SaveFile(forges = forges.records(), smiths = smiths.snapshot()))
    }
}
