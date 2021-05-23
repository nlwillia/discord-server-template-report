package nlw.template

typealias ChannelType = discord4j.core.`object`.entity.channel.Channel.Type

val ChannelType.displayName get() = toString().removePrefix("GUILD_").lowercase().replaceFirstChar(Char::uppercase)

val defaultCategory = Channel(-1, "Uncategorized", ChannelType.GUILD_CATEGORY, null, arrayOf())

/** Groups and traverses channels by category. */
fun channelsByCategory(server: Server, consumer: (Category) -> Unit) {
	fun groupChannels(server: Server): List<Category> = (arrayOf(defaultCategory) + server.channels)
		.filter { it.type == ChannelType.GUILD_CATEGORY }
		.map { channel ->
			Category(
				channel,
				server.channels.filter { it.type != ChannelType.GUILD_CATEGORY && it.parent_id ?: defaultCategory.id == channel.id }.toList()
			)
		}.toList()

	groupChannels(server).asSequence().filter { !it.isEmpty() }.forEach(consumer)
}

/** Unifying interface for logic that is common between a Category and a regular Channel. */
interface HasOverwrites {
	val type: ChannelType
	val permission_overwrites: Array<PermissionOverwrite>
}

/** Wraps a category-type channel and its children. */
data class Category(val channel: Channel, val children: List<Channel>) : HasOverwrites {
	val id by channel::id
	val name by channel::name
	override val type by channel::type
	override val permission_overwrites by channel::permission_overwrites

	fun isEmpty() = children.isEmpty()
}
