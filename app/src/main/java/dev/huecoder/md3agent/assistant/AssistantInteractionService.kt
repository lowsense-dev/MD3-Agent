package dev.huecoder.md3agent.assistant

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import dev.huecoder.md3agent.MainActivity

class AssistantInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        live = this
    }

    override fun onShutdown() {
        if (live === this) live = null
        super.onShutdown()
    }

    companion object {
        private var live: AssistantInteractionService? = null

        /** Opens the assistant: the overlay sheet when MD3 is the active assistant, otherwise a chat. */
        fun summon(context: Context) {
            val service = live
            if (service != null) {
                service.showSession(Bundle(), VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT)
            } else {
                context.startActivity(MainActivity.chatIntent(context, chatId = null))
            }
        }
    }
}
