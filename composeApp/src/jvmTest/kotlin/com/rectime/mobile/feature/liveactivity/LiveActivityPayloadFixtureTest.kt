package com.rectime.mobile.feature.liveactivity

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** Swift側の実際のDTOで読み込む、共通エンコーダーの検証データ。 */
class LiveActivityPayloadFixtureTest {
    @Test fun writePayloadForNativeDecoderVerification() {
        val state = LiveActivityState("user", LivePersonalPlan(1, "バスケットボール🏀", listOf(4, 5), "体育館", "体育館２入口"), 3)
        val json = encodeLiveActivityState(state)
        assertTrue(json.encodeToByteArray().size < 3500)
        val path = Path.of("build", "codex-validation", "native-payload.json")
        Files.createDirectories(path.parent)
        Files.writeString(path, json)
    }
}
