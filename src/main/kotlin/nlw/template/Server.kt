package nlw.template

import discord4j.core.`object`.PermissionOverwrite
import discord4j.core.`object`.entity.channel.Channel
import discord4j.rest.util.Permission
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.html.*
import nlw.template.PermissionExtn.Companion.channelTypes
import nlw.template.PermissionExtn.Companion.clientName
import nlw.template.PermissionExtn.Companion.clientOrder
import nlw.template.PermissionExtn.Companion.hexValue
import nlw.template.PermissionExtn.Companion.isUsedWith
import nlw.template.PermissionExtn.Companion.prerequisites

/** Basic ktor server runs on a port number passed as an argument. */
fun main(args: Array<String>) {
	embeddedServer(Netty, args.getOrNull(0)?.toInt() ?: 8080, watchPaths = listOf("ServerKt"), module = Application::module).start(true)
}

fun Application.module() {
	install(DefaultHeaders)
	install(CallLogging)
	install(Routing) {
		get("/styles.css") {
			call.respondText(assetText("styles.css"), ContentType.Text.CSS)
		}

		get("/") {
			val templateId = call.parameters["template"] ?: ""

			if (templateId.isBlank()) {
				call.respondHtml(block = formResponse())
			} else {
				runCatching { Template.fromKey(templateId) }.fold({
					call.respondHtml(block = reportResponse(it.serialized_source_guild))
				}, {
					call.respondHtml(block = formResponse(error = it.message))
				})
			}
		}
	}
}

private fun assetText(filename: String) = Server::class.java.getResource("/$filename")!!.readText()

private fun formResponse(error: String? = null): HTML.() -> Unit = {
	head {
		title { +"Server Template Lookup" }
		style { unsafe { +assetText("styles.css") } }
	}
	body {
		h1 {
			+"Server Template Lookup"
		}
		if (error != null) {
			p("error") {
				+error
			}
		}
		form(classes = "templateForm") {
			textInput {
				name = "template"
				placeholder = "https://discord.new/<key>"
			}
			getButton { +"Lookup" }
		}
		unsafe { +assetText("about.html") }
	}
}

private fun reportResponse(server: Server): HTML.() -> Unit = {
	head {
		title { +"Server Template Permissions" }
		style { unsafe { +assetText("styles.css") } }
		//styleLink("/styles.css")
	}

	body {
		h1 {
			+server.name
			entity(Entities.nbsp)
			small {
				a("/") {
					title = "Back"
					entity(Entities.times)
				}
			}
		}

		h2 { +"Roles" }
		ul("roles") {
			server.roles.filter { !it.isCosmetic(server) }.forEach { role ->
				li {
					span("role") {
						title = "id=${role.id}"
						+role.name
					}

					role.permissions.sortedWith(clientOrder).forEach {
						val unnecessary = server.roleIndex.isUnnecessary(it, role)
						span("role-permission" + if (unnecessary) " unnecessary" else "") {
							title = if (unnecessary) Rule.RULE1.fullDescription else ""
							+it.clientName
						}
					}
				}
			}
		}

		server.roles.filter { it.isCosmetic(server) }.let {
			if (it.isNotEmpty()) {
				h3 { +"Cosmetic Roles" }
				ul("cosmeticRoles") {
					it.forEach {
						li("cosmetic-role") {
							title = "id=${it.id}"
							+it.name
						}
					}
				}
			}
		}

		fun printOverwrites(channel: HasOverwrites) {
			val analysis = OverwriteResult.analyzePermissions(server.roleIndex, channel)

			ul("overwrites") {
				channel.permission_overwrites.asSequence().filter { it.type == PermissionOverwrite.Type.ROLE }.forEach { po ->
					val role = server.roleIndex.byId(po.id)
					val result = analysis[role] ?: error("Missing analysis result for overwrite: $po")
					li {
						span("overwrite" + if (result.unnecessary) " unnecessary" else "") {
							title = ("id=${role.id}\n" + result.description).trim()
							+role.name
						}

						if (po.allow.isNotEmpty() || po.deny.isNotEmpty()) {
							po.permissions.filter { it.isUsedWith(channel.type) }.sortedWith(clientOrder).forEach { p ->
								span("channel-permission " + (if (po.isAllow(p)) "allow" else "deny") + (if (result.isUnnecessary(p)) " unnecessary" else "")) {
									title = result.getDescription(p)
									+p.clientName
								}
							}
						}
					}
				}
			}
		}

		h2 { +"Channels" }
		ul("tree") {
			channelsByCategory(server) { category ->
				li {
					span("category") {
						title = "id=${category.id}"
						+category.name
					}
					printOverwrites(category)

					ul("channels") {
						category.children.forEach { channel ->
							li {
								span("channel") {
									title = "id=${channel.id}"
									if (channel.type == Channel.Type.GUILD_TEXT) {
										unsafe { +"&num;&nbsp;" }
									} else if (channel.type == Channel.Type.GUILD_VOICE) {
										unsafe { +"&smt;&nbsp;" }
									}
									+channel.name
								}

								if (category.permission_overwrites.contentEquals(channel.permission_overwrites)) {
									span("channel-permission sync") { +"In Sync" }
								} else {
									printOverwrites(channel)
								}
							}
						}
					}
				}
			}
		}

		unsafe { +assetText("rules.html") }
		ol {
			Rule.values().forEach {
				li { +it.description }
			}
		}

		h3 { +"Permission Types" }
		table("permission-types") {
			tr {
				th { +"Value" }
				th { +"Display Name" }
				th { +"Overwritable On" }
				th { +"Prerequisites" }
			}
			Permission.values().sortedWith(clientOrder).forEach { p ->
				tr {
					td { +p.hexValue }
					td { +p.clientName }
					td { +p.channelTypes.joinToString(", ") { it.displayName }.ifBlank { "Role" } }
					td { +p.prerequisites.joinToString(", ") { it.clientName }.ifBlank { "none" } }
				}
			}
		}
	}
}
