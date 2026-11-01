import com.mineinabyss.geary.papermc.schemas.GearyPrefabSchemas.gearyPrefabs
import com.mineinabyss.jsonschema.writer.writeSchemas
import com.mineinabyss.staminaclimb.config.StaminaConfig

fun main(args: Array<String>) = writeSchemas(args) {
    schema("staminaclimb/config", listOf("**/plugins/StaminaClimb/config.yml"), StaminaConfig.serializer())
    gearyPrefabs("staminaclimb", listOf("com.mineinabyss.staminaclimb"))
}
