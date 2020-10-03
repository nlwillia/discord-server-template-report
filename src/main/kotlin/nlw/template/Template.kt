@file:Suppress("EXPERIMENTAL_API_USAGE", "ArrayInDataClass")

package nlw.template

import discord4j.rest.util.PermissionSet
import io.ktor.util.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/*
	The following data layouts track the relevant parts of the servlet template json structure.
 */

@Serializable data class Template(val code: String, val serialized_source_guild: Server) {
	companion object {
		fun fromArg(arg: String) = runCatching { fromResource(arg) }.getOrElse { runCatching { fromFile(arg) }.getOrElse { fromKey(arg) } }

		fun fromResource(path: String) = fromJson(Template::class.java.getResource(path).readText())

		fun fromFile(path: String) = fromJson(File(".").combineSafe(path).readText())

		private val templateUrl = "(?:https://discord.new/)?(.*)".toRegex()

		fun fromKey(keyInput: String): Template {
			val key = templateUrl.find(keyInput)!!.groupValues[1]
			val url = URI("https://discord.com/api/v6/guilds/templates/${key}") // request from Discord; no authentication is required
			val client = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.followRedirects(HttpClient.Redirect.NEVER)
				.build()
			val response = client.send(HttpRequest.newBuilder(url).build()) { HttpResponse.BodySubscribers.ofString(Charsets.UTF_8) }
			if (response.statusCode() != HttpURLConnection.HTTP_OK) {
				throw IllegalStateException("Unexpected response from ${url}: ${response.statusCode()}")
			}
			return fromJson(response.body())
		}

		private fun fromJson(json: String) = Json { ignoreUnknownKeys = true }.decodeFromString<Template>(json)
	}
}

@Serializable data class Server(val name: String, val roles: Array<Role>, val channels: Array<Channel>)

@Serializable data class Role(val id: Long, val name: String, @Serializable(with = PermissionSetSerializer::class) val permissions: PermissionSet)

@Serializable data class PermissionOverwrite(
	val id: Long,
	@Serializable(with = PermissionTypeSerializer::class) val type: PermissionType, // won't ever be MEMBER because they aren't part of a template snapshot
	@Serializable(with = PermissionSetSerializer::class) val allow: PermissionSet,
	@Serializable(with = PermissionSetSerializer::class) val deny: PermissionSet
)

@Serializable data class Channel(
	val id: Long,
	val name: String,
	@Serializable(with = ChannelTypeSerializer::class) override val type: ChannelType,
	val parent_id: Long?,
	override val permission_overwrites: Array<PermissionOverwrite>
) : HasOverwrites

//------------------------------------------------------------

@Serializer(forClass = ChannelType::class)
object ChannelTypeSerializer : KSerializer<ChannelType> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("discord4j.ChannelTypeSerializer", PrimitiveKind.INT)
	override fun serialize(encoder: Encoder, value: ChannelType) = encoder.encodeInt(value.value)
	override fun deserialize(decoder: Decoder): ChannelType = ChannelType.of(decoder.decodeInt())
}

@Serializer(forClass = PermissionType::class)
object PermissionTypeSerializer : KSerializer<PermissionType> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("discord4j.PermissionTypeSerializer", PrimitiveKind.INT)

	override fun serialize(encoder: Encoder, value: PermissionType) = encoder.encodeInt(
		when (value) {
			PermissionType.UNKNOWN -> -1
			PermissionType.ROLE -> 0
			PermissionType.MEMBER -> 1
		}
	)

	override fun deserialize(decoder: Decoder): PermissionType = when (decoder.decodeInt()) {
		0 -> PermissionType.ROLE
		1 -> PermissionType.MEMBER
		else -> PermissionType.UNKNOWN
	}
}

@Serializer(forClass = PermissionSet::class)
object PermissionSetSerializer : KSerializer<PermissionSet> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("discord4j.PermissionSetSerializer", PrimitiveKind.LONG)
	override fun serialize(encoder: Encoder, value: PermissionSet) = encoder.encodeLong(value.rawValue)
	override fun deserialize(decoder: Decoder): PermissionSet = PermissionSet.of(decoder.decodeLong())

	// note:
	// In API v8, all permissions—including allow and deny fields in overwrites—are serialized as strings. There are also no longer _new permission fields; all new permissions are rolled back into the base field.
	// In API v6, the permissions, allow, and deny fields in roles and overwrites are still serialized as a number; however, these numbers shall not grow beyond 31 bits. During the remaining lifetime of API v6, all new permission bits will only be introduced in permissions_new, allow_new, and deny_new. These _new fields are just for response serialization; requests with these fields should continue to use the original permissions, allow, and deny fields, which accept both string or number values.
}
