package com.r0ine.auraforge.core

import com.r0ine.auraforge.AuraForgePlugin
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import java.util.Locale

fun tag(key: String, value: String): TagResolver = Placeholder.unparsed(key, value)

fun tag(key: String, value: ComponentLike): TagResolver = Placeholder.component(key, value)

fun rich(key: String, value: String): TagResolver = Placeholder.parsed(key, value)

fun Component.noItalic(): Component = decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)

private val TURKISH = Locale.of("tr")

fun seconds(value: Double): String = String.format(TURKISH, "%.1f", value)

class Text(private val plugin: AuraForgePlugin) {

    private val mini = MiniMessage.miniMessage()

    fun raw(key: String): String = when (val value = plugin.settings.messages[key]) {
        is JsonPrimitive -> value.content
        is JsonArray -> value.joinToString("<newline>") { (it as? JsonPrimitive)?.content.orEmpty() }
        else -> key
    }

    fun parse(input: String, vararg tags: TagResolver): Component =
        mini.deserialize(input, TagResolver.resolver(Placeholder.parsed("prefix", raw("prefix")), *tags))

    fun render(key: String, vararg tags: TagResolver): Component = parse(raw(key), *tags)

    fun item(key: String, vararg tags: TagResolver): Component = render(key, *tags).noItalic()

    fun lines(key: String, vararg tags: TagResolver): List<Component> =
        when (val value = plugin.settings.messages[key]) {
            is JsonArray -> value.map { parse((it as? JsonPrimitive)?.content.orEmpty(), *tags).noItalic() }
            null -> emptyList()
            else -> listOf(item(key, *tags))
        }

    fun send(to: Audience, key: String, vararg tags: TagResolver) = to.sendMessage(render(key, *tags))

    fun bar(to: Audience, key: String, vararg tags: TagResolver) = to.sendActionBar(render(key, *tags))
}
