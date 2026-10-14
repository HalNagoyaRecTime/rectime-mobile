package com.rectime.mobile.feature.liveactivity

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** Swift側の実際のDTOで読み込む、共通エンコーダーの検証データ。 */
class LiveActivityPayloadFixtureTest {
    @Test fun writePayloadForNativeDecoderVerification() {
        val state = LiveActivityState("user", listOf(LiveMatch(1, "バスケットボール🏀", "体育館", 1_800_000_000, 1_800_003_600)),
            1, LiveGathering(1, "競技", 22, 4, 1_800_000_900, "体育館２入口", null), null,
            1_800_000_900, null, true)
        val json = encodeLiveActivityState(state)
        assertTrue(json.encodeToByteArray().size < 3500)
        val path = Path.of("build", "codex-validation", "native-payload.json")
        Files.createDirectories(path.parent)
        Files.writeString(path, json)
    }
}
