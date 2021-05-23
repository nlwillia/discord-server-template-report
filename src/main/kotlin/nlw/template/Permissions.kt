package nlw.template

import discord4j.rest.util.Permission
import discord4j.rest.util.PermissionSet
import nlw.template.PermissionExtn.Companion.isUsedWith
import nlw.template.PermissionExtn.Companion.prerequisites
import java.lang.IllegalStateException

typealias PermissionType = discord4j.core.`object`.PermissionOverwrite.Type

/** Permission extensions */
class PermissionExtn(val order: Int, val clientName: String, val channelTypes: Set<ChannelType>, val prerequisites: Set<Permission>) {
	companion object {
		private val index = run {
			// ChannelTypes where the permission appears on its overwrite list.
			val textChannels = linkedSetOf(ChannelType.GUILD_CATEGORY, ChannelType.GUILD_TEXT)
			val voiceChannels = linkedSetOf(ChannelType.GUILD_CATEGORY, ChannelType.GUILD_VOICE)
			val textAndVoiceChannels = linkedSetOf(ChannelType.GUILD_CATEGORY, ChannelType.GUILD_TEXT, ChannelType.GUILD_VOICE)

			// Prerequisite permission sets
			val viewChannel = linkedSetOf(Permission.VIEW_CHANNEL)
			val viewChannelOrSendMessages = linkedSetOf(Permission.VIEW_CHANNEL, Permission.SEND_MESSAGES)
			val viewChannelOrAddReactions = linkedSetOf(Permission.VIEW_CHANNEL, Permission.ADD_REACTIONS)

			var order = 0

			// Enrichments interpreted from https://discord.com/developers/docs/topics/permissions
			linkedMapOf(
				Permission.ADMINISTRATOR to PermissionExtn(++order, "Administrator", emptySet(), emptySet()),
				Permission.VIEW_AUDIT_LOG to PermissionExtn(++order, "View Audit Log", emptySet(), emptySet()),
				Permission.VIEW_GUILD_INSIGHTS to PermissionExtn(++order, "View Server Insights", emptySet(), emptySet()), // Community Only
				Permission.MANAGE_GUILD to PermissionExtn(++order, "Manage Server", emptySet(), emptySet()),
				Permission.MANAGE_ROLES to PermissionExtn(++order, "Manage Permissions", textAndVoiceChannels, emptySet()),
				Permission.MANAGE_CHANNELS to PermissionExtn(++order, "Manage Channel", textAndVoiceChannels, emptySet()),
				Permission.KICK_MEMBERS to PermissionExtn(++order, "Kick Members", emptySet(), emptySet()),
				Permission.BAN_MEMBERS to PermissionExtn(++order, "Ban Members", emptySet(), emptySet()),
				Permission.CREATE_INSTANT_INVITE to PermissionExtn(++order, "Create Invite", textAndVoiceChannels, emptySet()),
				Permission.CHANGE_NICKNAME to PermissionExtn(++order, "Change Nickname", emptySet(), emptySet()),
				Permission.MANAGE_NICKNAMES to PermissionExtn(++order, "Manage Nicknames", emptySet(), emptySet()),
				Permission.MANAGE_EMOJIS to PermissionExtn(++order, "Manage Emojis", emptySet(), emptySet()),
				Permission.MANAGE_WEBHOOKS to PermissionExtn(++order, "Manage Webhooks", textAndVoiceChannels, emptySet()),

				// Category=Read Text Channels & See Voice Channels, Text=Read Messages, Voice=View Channel
				Permission.VIEW_CHANNEL to PermissionExtn(++order, "View Channel", textAndVoiceChannels, emptySet()),

				// Text
				Permission.READ_MESSAGE_HISTORY to PermissionExtn(++order, "Read Message History", textChannels, viewChannel),
				Permission.SEND_MESSAGES to PermissionExtn(++order, "Send Messages", textChannels, viewChannel),
				Permission.SEND_TTS_MESSAGES to PermissionExtn(++order, "Send TTS Messages", textChannels, viewChannelOrSendMessages),
				Permission.MANAGE_MESSAGES to PermissionExtn(++order, "Manage Messages", textChannels, viewChannelOrSendMessages),
				Permission.EMBED_LINKS to PermissionExtn(++order, "Embed Links", textChannels, viewChannelOrSendMessages),
				Permission.ATTACH_FILES to PermissionExtn(++order, "Attach Files", textChannels, viewChannelOrSendMessages),
				Permission.MENTION_EVERYONE to PermissionExtn(++order, "Mention @everyone", textChannels, viewChannelOrSendMessages),
				Permission.USE_EXTERNAL_EMOJIS to PermissionExtn(++order, "Use External Emojis", textChannels, viewChannelOrAddReactions),
				Permission.ADD_REACTIONS to PermissionExtn(++order, "Add Reactions", textChannels, viewChannel),
				// Voice
				Permission.CONNECT to PermissionExtn(++order, "Connect", voiceChannels, viewChannel),
				Permission.SPEAK to PermissionExtn(++order, "Speak", voiceChannels, viewChannel),
				Permission.STREAM to PermissionExtn(++order, "Video", voiceChannels, viewChannel),
				Permission.MUTE_MEMBERS to PermissionExtn(++order, "Mute Members", voiceChannels, viewChannel),
				Permission.DEAFEN_MEMBERS to PermissionExtn(++order, "Deafen Members", voiceChannels, viewChannel),
				Permission.MOVE_MEMBERS to PermissionExtn(++order, "Move Members", voiceChannels, viewChannel),
				Permission.USE_VAD to PermissionExtn(++order, "User Voice Activity", voiceChannels, viewChannel),
				Permission.PRIORITY_SPEAKER to PermissionExtn(++order, "Priority Speaker", voiceChannels, viewChannel),
				Permission.REQUEST_TO_SPEAK to PermissionExtn(++order, "Request to Speak", voiceChannels, viewChannel),
			)
		}

		val Permission.hexValue get() = "0x" + Integer.toHexString(value.toInt()).padStart(8, '0')

		/** List of channel types the permission is overwritable on. */
		val Permission.channelTypes get() = index[this]!!.channelTypes

		/** True if the permission appears on the ChannelType's overwrite sheet. */
		fun Permission.isUsedWith(type: ChannelType) = channelTypes.contains(type)

		/** UI display name of the permission. */
		val Permission.clientName get() = index[this]?.clientName ?: this.toString()

		/** Permissions that must be set for this permission to be relevant. */
		val Permission.prerequisites get() = index[this]?.prerequisites ?: emptySet()

		/** Sort in UI display order. */
		val clientOrder = Comparator<Permission> { a, b -> a.order - b.order }
		private val Permission.order get() = index[this]?.order ?: -1
	}
}

//------------------------------------------------------------
/*
	Enhancements here rather than in Template because they aren't involved with the serialized format
	and because they're more relevant to permission analysis.
 */

/** Map index of a list of roles. */
class Roles(roles: Array<Role>) {
	private val byId = roles.map { it.id to it }.toMap()
	val everyone = byId(0)

	fun byId(id: Long) = byId[id] ?: throw IllegalStateException("No role found for id: $id")

	/** A custom role permission is unnecessary if it's enabled on the @everyone role. */
	fun isUnnecessary(permission: Permission, role: Role) =
		role != everyone && role.isAllow(permission) && everyone.isAllow(permission) // this is sort of a one-off case
}

private val Server.lazyRoles get() = lazy { Roles(roles) }
val Server.roleIndex get() = lazyRoles.value

fun Role.isCosmetic(server: Server) =
	this.permissions.isEmpty() && !server.channels.any { it.permission_overwrites.any { it.isRole(this) } }

fun Role.isAllow(p: Permission) = permissions.contains(p)

fun Channel.roleOverwrite(role: Role) = permission_overwrites.asSequence().filter { it.isRole(role) }.firstOrNull()

fun PermissionOverwrite.isRole(role: Role) = type == PermissionType.ROLE && id == role.id
fun PermissionOverwrite.isEveryone() = type == PermissionType.ROLE && id == 0L
fun PermissionOverwrite.overrides(p: Permission) = allow.contains(p) || deny.contains(p)

val PermissionOverwrite.permissions get() = allow.asSequence() + deny.asSequence()
fun PermissionOverwrite.isAllow(p: Permission) = allow.contains(p)
fun PermissionOverwrite.isDeny(p: Permission) = deny.contains(p)

val emptyEveryoneOverwrite = PermissionOverwrite(0, PermissionType.ROLE, PermissionSet.none(), PermissionSet.none())

//------------------------------------------------------------

/** Collect lint rules for documentation. */
enum class Rule(val description: String) {
	RULE1("A custom role permission is unnecessary if it's enabled on the @everyone role."),
	RULE2("A channel @everyone allow is unnecessary if it's enabled on the @everyone role."),
	RULE3("A custom overwrite role is unnecessary if it contains no permission modifications."),
	RULE4("A custom overwrite permission is unnecessary if there are no channels it would pertain to."),
	RULE5("A custom overwrite permission is unnecessary if all compatible non-sync'd channels override it."),
	RULE6("A custom allow is unnecessary if local @everyone allows it or if local @everyone doesn't deny it and it's allowed by the global role or @everyone."),
	RULE7("A custom deny is unnecessary if local @everyone denies it or if local @everyone doesn't allow it and it's not allowed by the global role or @everyone."),
	RULE8("A custom overwrite permission is unnecessary if a permission it depends on is not set.");

	val fullDescription get() = "$this: $description"
}

/** Collection of role-level and overwrite-permission level rule warnings detected. */
data class OverwriteResult(
	val roleRules: Set<Rule> = mutableSetOf(),
	val permissionRules: Map<Permission, Set<Rule>> = mutableMapOf(),
) {
	private fun roleRule(rule: Rule) {
		(roleRules as MutableSet).add(rule)
	}

	private fun permissionRule(permission: Permission, rule: Rule) {
		((permissionRules as MutableMap).getOrPut(permission) { mutableSetOf() } as MutableSet).add(rule)
	}

	companion object {
		fun analyzePermissions(roles: Roles, channel: HasOverwrites): Map<Role, OverwriteResult> {
			val results = mutableMapOf<Role, OverwriteResult>()

			if (channel.permission_overwrites.isNotEmpty()) {

				val everyone = roles.everyone
				val everyoneOverwrite = channel.permission_overwrites.firstOrNull { it.isEveryone() } ?: emptyEveryoneOverwrite // sometimes everyone is missing in the source data

				channel.permission_overwrites.asSequence().filter { it.type == PermissionType.ROLE }.forEach { po ->
					val role = roles.byId(po.id)

					val result = OverwriteResult()
					results[role] = result

					if (po == everyoneOverwrite) {

						// A channel @everyone allow is unnecessary if it's enabled on the @everyone role.
						po.allow.forEach {
							if (everyone.isAllow(it)) {
								result.permissionRule(it, Rule.RULE2)
							}
						}

					} else {

						// A custom overwrite role is unnecessary if it contains no permission modifications.
						if (po.allow.isEmpty() && po.deny.isEmpty()) {
							result.roleRule(Rule.RULE3)
						}

						// A custom allow is unnecessary if local @everyone allows it or if local @everyone doesn't deny it and it's allowed by the global role or @everyone.
						po.allow.forEach { p ->
							if (everyoneOverwrite.isAllow(p) || !everyoneOverwrite.isDeny(p) && (everyone.isAllow(p) || role.isAllow(p))) {
								result.permissionRule(p, Rule.RULE6)
							}
						}

						// A custom deny is unnecessary if local @everyone denies it or if local @everyone doesn't allow it and it's not allowed by the global role or @everyone.
						po.deny.forEach { p ->
							if (everyoneOverwrite.isDeny(p) || !everyoneOverwrite.isAllow(p) && !everyone.isAllow(p) && !role.isAllow(p)) {
								result.permissionRule(p, Rule.RULE7)
							}
						}

						// A custom overwrite permission is unnecessary if a permission it depends on is not set.
						po.permissions.forEach { p ->
							if (p.prerequisites.any { r -> po.isDeny(r) || everyoneOverwrite.isDeny(r) && !po.isAllow(r) || !everyoneOverwrite.isDeny(r) && !everyone.isAllow(r) && !role.isAllow(r) }) {
								result.permissionRule(p, Rule.RULE8)
							}
						}
					}

					if (channel is Category) {
						// A custom overwrite permission is unnecessary if there are no channels it would pertain to.
						po.permissions.forEach { p ->
							if (channel.children.none { p.isUsedWith(it.type) }) {
								result.permissionRule(p, Rule.RULE4)
							}
						}

						// A custom overwrite permission is unnecessary if all compatible non-sync'd channels override it.
						po.permissions.forEach { p ->
							if (channel.children.all { it.roleOverwrite(role)?.overrides(p) == true && !channel.permission_overwrites.contentEquals(it.permission_overwrites) }) {
								result.permissionRule(p, Rule.RULE5)
							}
						}
					}
				}
			}
			return results
		}
	}

	val unnecessary get() = roleRules.isNotEmpty()
	val description get() = roleRules.joinToString("\n") { it.fullDescription }

	fun isUnnecessary(permission: Permission) = permissionRules[permission]?.isNotEmpty() ?: false
	fun getDescription(permission: Permission) = permissionRules[permission]?.joinToString("\n") { it.fullDescription } ?: ""
}
