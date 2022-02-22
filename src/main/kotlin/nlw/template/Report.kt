@file:Suppress("EXPERIMENTAL_API_USAGE", "ArrayInDataClass")

package nlw.template

/** Simple text report of role/channel/overwrite information. */
fun main(args: Array<String>) {
	val template = Template.fromArg(args.getOrNull(0) ?: throw IllegalArgumentException("expected resource path"))
	dump(template.serialized_source_guild)
}

fun dump(server: Server) {

	println("Server: ${server.name}\n")

	fun printOverwrite(po: PermissionOverwrite) {
		val prefix = when (po.type) {
			PermissionType.ROLE -> server.roleIndex.byId(po.id).let { "${it.name} (${it.id})" }
			else -> po.type.toString()
		}

		print("\t${prefix} = ")
		println(po.permissions.joinToString(", ") { (if (po.isAllow(it)) "+" else "-") + it })
	}

	server.roles.filter { !it.isCosmetic(server) }.forEach {
		println("${it.name} (${it.id}) = " + it.permissions.joinToString(", ") { "+$it" })
	}
	println("Cosmetic Roles: " + server.roles.filter { it.isCosmetic(server) }.joinToString(", ") { "${it.name} (${it.id})" })
	println()

	channelsByCategory(server) { category ->
		println("${category.name} (${category.id})")

		category.permission_overwrites.forEach(::printOverwrite)
		println()

		category.children.forEach { channel ->
			println("\t${channel.name} (${channel.id})")
			if (category.equalsOverwrites(channel)) {
				println("\t\tIn Sync")
			} else {
				channel.permission_overwrites.forEach { print("\t"); printOverwrite(it) }
			}
		}
		println()
	}
}
