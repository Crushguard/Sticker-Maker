package com.piptechnologies.stickermaker.feature.namepack.engine

/** The four template characters, in the design's tile order ("Who says it?"). Labels are proper names. */
enum class Character(val id: String, val label: String) {
    MANGO("mango", "Mango"),
    PINKY("pinky", "Pinky"),
    BUNNY("bunny", "Bunny"),
    CAPY("capy", "Capy");

    companion object {
        fun byId(id: String?): Character = entries.firstOrNull { it.id == id } ?: MANGO
    }
}

/** Who the pack is for, in chip order. Mom and Friend are family: Sweet only, family phrases. */
enum class Relation(val id: String, val family: Boolean = false) {
    GIRLFRIEND("girlfriend"),
    BOYFRIEND("boyfriend"),
    WIFE("wife"),
    HUSBAND("husband"),
    CRUSH("crush"),
    PARTNER("partner"),
    MOM("mom", family = true),
    FRIEND("friend", family = true);

    companion object {
        fun byId(id: String?): Relation = entries.firstOrNull { it.id == id } ?: GIRLFRIEND
    }
}

/** The pack's tone. Nothing explicit: Flirty is playful. */
enum class Tone(val id: String) {
    SWEET("sweet"),
    FLIRTY("flirty");

    companion object {
        fun byId(id: String?): Tone = entries.firstOrNull { it.id == id } ?: SWEET
    }
}

/** The 12 phrase slots, in template and grid order. [key] is the key in the design's JSON files. */
enum class Slot(val key: String) {
    LOVE_YOU("love_you"),
    MISS_YOU("miss_you"),
    GOOD_MORNING("good_morning"),
    GOOD_NIGHT("good_night"),
    KISS("kiss"),
    HUG("hug"),
    SORRY("sorry"),
    BE_MINE("be_mine"),
    FOR_YOU("for_you"),
    CALL_ME("call_me"),
    OUR_NAMES("our_names"),
    NAME_ONLY("name_only");

    companion object {
        fun byKey(key: String): Slot? = entries.firstOrNull { it.key == key }
    }
}
