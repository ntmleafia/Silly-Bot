package com.leafia.sillybot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.message.GenericMessageEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.thread.GenericThreadEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.internal.entities.channel.concrete.ThreadChannelImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

public class SillyBot {
	private static final Logger logger = LoggerFactory.getLogger(SillyBot.class);
	private static JDA jda;
	private static final File tokenDir = new File("D:\\sillybot_token.txt"); // top secret!!
	private static SelfUser self;
	public enum ServerType {
		CURSED,WARFACTORY,DEVELOPMENT,UNKNOWN
	}
	// this is so crappy
	public static ServerType getServerType(Guild server) {
		String name = server.getName();
		if (name.equalsIgnoreCase("silly bot development"))
			return ServerType.DEVELOPMENT;
		if (name.equalsIgnoreCase("warfactory official"))
			return ServerType.WARFACTORY;
		return ServerType.UNKNOWN;
	}
	public static class SillyListener extends ListenerAdapter {
		@Override
		public void onMessageReceived(MessageReceivedEvent event) {
			if (event.getAuthor().equals(self)) return;
			ServerType type = getServerType(event.getGuild());
			Channel chan = event.getChannel();
			if (type.equals(ServerType.DEVELOPMENT) && chan.getName().equalsIgnoreCase("resources"))
				return;
			if (type.equals(ServerType.DEVELOPMENT) || type.equals(ServerType.WARFACTORY))
				tryQNA(event,type);
			//if (true) return;
			//event.getMessage().getChannel().sendMessage("Channel class: "+chan.getClass().getName()).queue();
			//event.getMessage().getChannel().sendMessage("name: "+impl.getName()+", count: "+impl.getTotalMessageCount()).queue();
			//event.getMessage().getChannel().sendMessage("forum name: "+forum.getName()).queue();
		}
		public static void tryQNA(MessageReceivedEvent event,ServerType type) {
			Channel chan = event.getChannel();
			if (chan instanceof ThreadChannelImpl thread) {
				if (thread.getParentChannel() instanceof ForumChannel forum) {
					// if on development server, reply infinitely
					// if on actual WF server, only reply on the first message of the thread
					if (thread.getMessageCount() <= 1 || type.equals(ServerType.DEVELOPMENT)) {
						if (forum.getName().equalsIgnoreCase("issues-and-qna"))
							answerQNA(event.getMessage(),thread,event);
					}
				}
			}
		}
		public static void answerQNA(Message data,ThreadChannelImpl thread,MessageReceivedEvent event) {
			String msg = data.getContentDisplay().toLowerCase();
			String title = thread.getName();
			if (threadContainsRegexes(title,msg,"he") && threadContainsRegexes(title,msg,"rf","fe")) {
				thread.sendMessage(new MessageCreateBuilder()
								.setContent(
										"HE/RF converters are purely decorative in NTM:CE. Cables do not connect (see picture 1), " +
										"nor the block has any functionality at all besides spitting a wall of text on your screen." +
										"\nInstead, NTM:CE does HE/RF conversion automatically. This can be done in 2 ways:" +
										"\n\n## Connecting RF cables to HE-based machines directly" +
										"\nTo get it working, you have to connect cables that use RF directly from your HE-based machines " +
										"(NTM cables does NOT connect to RF cables, as I repeat, you have to connect the cable to machines directly)." +
										"\n\nSecondly, you have to make sure the RF-based cable you're using explicitly extracts energy from connected devices." +
										"\nFor example with Mekanism, by just connecting the cable to the battery, it doesn't work. (see picture 2)" +
										"\nFor this to work, the cable must be set to Pull connection type. (see picture 3)" +
										"\n\nI don't know much about other mods, but for example with cables from Thermal series you would need a servo, and so on." +
										"\nIf the mod has no feature to make the cable explicitly pull energy from connected devices, well, you're doomed." +
										"\n**Oh and don't forget to enable output on your battery socket. I'd cry if you forget to do that while reaching this far.**" +
										"\n\n## Automatic cable conversion" +
										"\nAlternatively, you can enable autoCableConversion in hbm.cfg to make cables from NTM also transfer RF. (see picture 4)" +
										"\nYou can find this file under hbm/config folder in your Minecraft installation folder." +
										"\n\nHope this helps!"
								)
								.addFiles(
										getUpload("qna/herf/converter.png"),
										getUpload("qna/herf/energyinvalid.png"),
										getUpload("qna/herf/energyworking.png"),
										getUpload("qna/herf/config.png")
								)
								.build()
				).queue();
			}
		}
	}
	public static FileUpload getUpload(String resource) {
		String[] a = resource.split("/");
		return FileUpload.fromData(getStream(resource),a[a.length-1]);
	}
	public static InputStream getStream(String resource) {
		return SillyBot.class.getClassLoader().getResourceAsStream(resource);
	}
	public static boolean threadContainsRegexes(String title,String msg,String... w) {
		for (String ws : w) {
			if (containsRegex(title,ws))
				return true;
			if (containsRegex(msg,ws))
				return true;
		}
		return false;
	}
	public static boolean containsRegexes(String s,String... w) {
		for (String ws : w) {
			if (containsRegex(s,ws))
				return true;
		}
		return false;
	}
	public static boolean containsRegex(String s,String w) {
		return (" "+s.toLowerCase()+" ").matches(".*\\s"+w+"\\s.*");
	}
	static void main(String[] args) {
		try {
			String token = Files.readString(tokenDir.toPath());
			List<GatewayIntent> intents = new ArrayList<>(EnumSet.allOf(GatewayIntent.class));
			// remove unnecessary privileged intents
			intents.remove(GatewayIntent.GUILD_PRESENCES);
			intents.remove(GatewayIntent.GUILD_MEMBERS);
			jda = JDABuilder.createDefault(token)
					.enableIntents(intents)
					.addEventListeners(new SillyListener())
					.build();
			self = jda.getSelfUser();
			jda.awaitReady();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
