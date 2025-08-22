package xyz.block.trailblaze.logs.client

import ai.koog.agents.core.tools.ToolDescriptor
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.serializer
import xyz.block.trailblaze.agent.model.AgentTaskStatus
import xyz.block.trailblaze.api.MaestroDriverActionType
import xyz.block.trailblaze.logs.client.temp.registerTrailblazeToolSerializer
import xyz.block.trailblaze.logs.model.SessionStatus
import xyz.block.trailblaze.toolcalls.TrailblazeTool
import xyz.block.trailblaze.toolcalls.TrailblazeToolResult
import kotlin.reflect.KClass

object TrailblazeJson {

  fun createTrailblazeJsonInstance(
    allToolClasses: Map<ToolDescriptor, KClass<out TrailblazeTool>>,
    serializerModuleModifier: (SerializersModuleBuilder) -> Unit = { },
  ): Json = Json {
    classDiscriminator = "class" // Key to determine subclass
    ignoreUnknownKeys = true // Avoids errors on unknown fields
    isLenient = true // Allows unquoted strings & other relaxed parsing
    prettyPrint = true
    @OptIn(InternalSerializationApi::class)
    serializersModule = SerializersModule {
      polymorphicDefaultSerializer(TrailblazeLog::class) { value ->
        value::class.serializer() as? KSerializer<TrailblazeLog>
      }
      polymorphicDefaultSerializer(MaestroDriverActionType::class) { value ->
        value::class.serializer() as? KSerializer<MaestroDriverActionType>
      }
      polymorphicDefaultSerializer(TrailblazeToolResult::class) { value ->
        value::class.serializer() as? KSerializer<TrailblazeToolResult>
      }
      polymorphicDefaultSerializer(AgentTaskStatus::class) { value ->
        value::class.serializer() as? KSerializer<AgentTaskStatus>
      }
      polymorphicDefaultSerializer(SessionStatus::class) { value ->
        value::class.serializer() as? KSerializer<SessionStatus>
      }

      this.registerTrailblazeToolSerializer(allToolClasses)

      serializerModuleModifier(this)
    }
  }

  var defaultWithoutToolsInstance: Json = createTrailblazeJsonInstance(emptyMap())
}
