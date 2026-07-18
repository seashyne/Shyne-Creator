package seashyne.shynecore.voice;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophoneMuteEvent;
import de.maxhenkel.voicechat.api.events.VoicechatDisableEvent;

public final class ShyneVoicechatPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return "shyne_creator_microphone";
    }

    @Override
    public void initialize(VoicechatApi api) {
        ShyneMicrophoneState.setInstalled(true);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientSoundEvent.class, event ->
            ShyneMicrophoneState.acceptAudio(event.getRawAudio(), event.isWhispering())
        );
        registration.registerEvent(MicrophoneMuteEvent.class, event ->
            ShyneMicrophoneState.setMuted(event.isDisabled())
        );
        registration.registerEvent(VoicechatDisableEvent.class, event ->
            ShyneMicrophoneState.setDisabled(event.isDisabled())
        );
        registration.registerEvent(ClientVoicechatConnectionEvent.class, event ->
            ShyneMicrophoneState.setConnected(event.isConnected())
        );
    }
}
