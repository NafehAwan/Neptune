plugins {
    id("dev.kikugie.stonecutter")
}

// The version the code in src/ is written for; 1.21.1 gets a converted copy.
stonecutter active "1.21.11"

stonecutter parameters {
    replacements {
        // names that changed between 1.21.1 and 1.21.11
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }
        string(current.parsed >= "1.21.11") {
            replace("MobEffects.MOVEMENT_SPEED", "MobEffects.SPEED")
        }
        string(current.parsed >= "1.21.11") {
            replace("MobEffects.DAMAGE_BOOST", "MobEffects.STRENGTH")
        }
    }
}
