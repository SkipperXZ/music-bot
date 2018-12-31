import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.managers.AudioManager;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class Vertex extends ListenerAdapter {
    public static void main(String[] args) throws Exception {
        JDA jda = new JDABuilder(AccountType.BOT)
                .setToken(Ref.TOKEN)
                .buildBlocking();

        jda.addEventListener(new Vertex());
    }

    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;
    private static MessageReceivedEvent userEvent;
    private static int volume = 100;
    GuildMusicManager musicManager;
    private Vertex() {
        this.musicManagers = new HashMap<>();

        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
    }

    private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
        long guildId = Long.parseLong(guild.getId());
        GuildMusicManager musicManager = musicManagers.get(guildId);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager);
            musicManagers.put(guildId, musicManager);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

        return musicManager;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String[] command = event.getMessage().getContentRaw().split(" ", 2);
        Guild guild = event.getGuild();
        userEvent = event;


        if (guild != null) {
            if ("เล่น".equals(command[0]) && command.length == 2) {

                if(isValidURL(command[1])) {
                    loadAndPlay(event.getTextChannel(), command[1]);
                }
                else {
                    try {
                        loadAndPlay(event.getTextChannel(), new YoutubeSearch().search(command[1]));
                    }catch (Exception e){}
                }


            } else if ("ข้าม".equals(command[0])) {
                skipTrack(event.getTextChannel());
            }else if ("ไป".equals(command[0])) {
                disconnectFromVoiceChannel(event.getTextChannel().getGuild().getAudioManager());
                sendMessage(event.getTextChannel(),"ไปก็ได้!");
            }else if ("มา".equals(command[0])) {
                connectToCallerVoiceChannel(event.getTextChannel().getGuild().getAudioManager());
                sendMessage(event.getTextChannel(),"มาและจ้า");
            }else if ("เสียง".equals(command[0]) && command.length == 2) {
                volume = Integer.parseInt(command[1]);
                if(musicManager != null){
                    musicManager.player.setVolume(volume);
                }
            }
        }

        super.onMessageReceived(event);
    }
    public static boolean isValidURL(String urlString)
    {
        try
        {
            URL url = new URL(urlString);
            url.toURI();
            return true;
        } catch (Exception exception)
        {
            return false;
        }
    }
    private void loadAndPlay(final TextChannel channel, final String trackUrl) {
        musicManager = getGuildAudioPlayer(channel.getGuild());

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                channel.sendMessage("เพิ่มเพลง " + track.getInfo().title).queue();
                play(channel.getGuild(), musicManager, track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack firstTrack = playlist.getSelectedTrack();

                if (firstTrack == null) {
                    firstTrack = playlist.getTracks().get(0);
                }

                channel.sendMessage("เพิ่มเพลง " + firstTrack.getInfo().title + " (first track of playlist " + playlist.getName() + ")").queue();

                play(channel.getGuild(), musicManager, firstTrack);
            }

            @Override
            public void noMatches() {
                channel.sendMessage("ไม่เจอเพลง " + trackUrl).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                channel.sendMessage("เล่นไม่ได้ " + exception.getMessage()).queue();
            }
        });
    }

    private void play(Guild guild, GuildMusicManager musicManager, AudioTrack track) {
        connectToCallerVoiceChannel(guild.getAudioManager());
        musicManager.scheduler.queue(track);
    }

    private void skipTrack(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
        musicManager.scheduler.nextTrack();

        channel.sendMessage("เปลี่ยนนนนเพลงงง ").queue();
    }

    private static void connectToFirstVoiceChannel(AudioManager audioManager) {
        if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
            for (VoiceChannel voiceChannel : audioManager.getGuild().getVoiceChannels()) {
                audioManager.openAudioConnection(voiceChannel);
                break;
            }
        }
    }
    private static void connectToCallerVoiceChannel(AudioManager audioManager) {
        if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
            VoiceChannel voiceChannel = userEvent.getMember().getVoiceState().getChannel();
            audioManager.openAudioConnection(voiceChannel);
        }
    }
    private static void disconnectFromVoiceChannel(AudioManager audioManager){
        audioManager.getGuild().getAudioManager().closeAudioConnection();
    }
    private static void sendMessage(final TextChannel channel,String message){
        channel.sendMessage(message).queue();
    }
}